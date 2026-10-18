/**
 * Playwright-CDP 多页面驱动（pw_driver.cjs 的超集）
 *
 * 相比单页驱动新增：
 * - `{ "op": "newPage", "name": "B", "url": "..." }` —— 在**全新 browser context**（隔离 localStorage/sessionStorage）
 *   打开一个页面，充当第二个用户；`name` 缺省按 B/C/D 递增。
 * - 任意步骤可带 `"page": "B"` 把动作路由到该页面（缺省 "A" = 原上下文第一个页面）。
 * - `{ "op": "closePage", "name": "B" }`
 * - `{ "op": "api", "method": "GET|POST", "url": "/api/...", "body": {...} }` —— 用**该页面的 token** 直接打接口，
 *   返回解包后的 `{status, code, message, data}`，用于契约探针。
 * - `{ "op": "token", "name": "B" }` —— 读该页面的 access token（localStorage 优先，回退 sessionStorage）。
 *
 * 其余 op 语义与 pw_driver.cjs 完全一致（goto/fill/click/press/select/waitFor/wait/waitText/text/value/
 * count/visible/enabled/text/disabledButtons/shot/eval/table/headers/bodyText/url/storage/upload/
 * clickRowBtn/rowBtn/dialogText/dialogFill/dialogBtn/toasts/confirmOpen/pickOption/tab/console）。
 *
 * 用法：node pw_multi.cjs <actions.json> [--report out.json]
 */
const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright')

const CDP = process.env.PW_CDP || 'http://127.0.0.1:9222'
const actionsFile = process.argv[2]
if (!actionsFile) {
  console.error('usage: node pw_multi.cjs <actions.json> [--report out.json]')
  process.exit(2)
}
const actions = JSON.parse(fs.readFileSync(actionsFile, 'utf8'))
let reportPath = null
const ri = process.argv.indexOf('--report')
if (ri > -1) reportPath = process.argv[ri + 1]

function locatorFor(page, s) {
  if (s.testid) return page.getByTestId(s.testid)
  if (s.role) {
    const opts = {}
    if (s.name !== undefined) opts.name = s.name
    if (s.exact !== undefined) opts.exact = s.exact
    return page.getByRole(s.role, opts)
  }
  if (s.text !== undefined) return page.getByText(s.text, { exact: !!s.exact })
  if (s.label !== undefined) return page.getByLabel(s.label, { exact: !!s.exact })
  if (s.placeholder !== undefined) return page.getByPlaceholder(s.placeholder, { exact: !!s.exact })
  if (s.sel) return page.locator(s.sel)
  throw new Error('no locator in step: ' + JSON.stringify(s))
}

;(async () => {
  const browser = await chromium.connectOverCDP(CDP)
  const baseCtx = browser.contexts()[0]

  const pages = {}
  const logs = {} // name -> { console: [], errors: [] }

  const attach = (name, page) => {
    logs[name] = { console: [], errors: [], ws: [] }
    page.setDefaultTimeout(Number(process.env.PW_TIMEOUT || 20000))
    page.on('websocket', (ws) => {
      const rec = { url: ws.url().slice(0, 140), closed: false, frames: 0, lastFrame: null }
      logs[name].ws.push(rec)
      ws.on('framereceived', (f) => {
        rec.frames++
        rec.lastFrame = String(f.payload).slice(0, 200)
      })
      ws.on('close', () => {
        rec.closed = true
      })
    })
    page.on('console', (m) => {
      if (['error', 'warning'].includes(m.type())) logs[name].console.push(`${m.type()}: ${m.text()}`.slice(0, 400))
    })
    page.on('pageerror', (e) => logs[name].errors.push(String(e).slice(0, 400)))
    page.on('requestfailed', (r) => {
      const t = (r.failure() && r.failure().errorText) || ''
      if (!/ERR_ABORTED/.test(t)) logs[name].errors.push(`REQFAIL ${r.method()} ${r.url()} ${t}`.slice(0, 400))
    })
    pages[name] = page
  }

  attach('A', baseCtx.pages()[0] || (await baseCtx.newPage()))

  let autoName = 0
  const out = []

  for (const step of actions) {
    const rec = { op: step.op, note: step.note, page: step.page }
    try {
      const target = step.page || 'A'
      const p = pages[target]
      if (!p && step.op !== 'newPage') throw new Error(`page '${target}' 不存在（先 newPage）`)

      switch (step.op) {
        case 'newPage': {
          const name = step.name || String.fromCharCode(66 + autoName++)
          const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
          const np = await ctx.newPage()
          attach(name, np)
          if (step.url) await np.goto(step.url, { waitUntil: 'domcontentloaded' })
          rec.value = name
          break
        }
        case 'closePage': {
          const name = step.name || 'B'
          if (pages[name]) {
            await pages[name].context().close().catch(() => {})
            delete pages[name]
          }
          rec.value = name
          break
        }
        case 'goto': {
          await p.goto(step.url, { waitUntil: step.waitUntil || 'domcontentloaded' })
          if (step.waitFor) await locatorFor(p, step.waitFor).first().waitFor({ state: 'visible' })
          rec.url = p.url()
          break
        }
        case 'reload': {
          await p.reload({ waitUntil: 'domcontentloaded' })
          rec.url = p.url()
          break
        }
        case 'click': {
          await locatorFor(p, step).first().click({ force: !!step.force })
          if (step.after) await p.waitForTimeout(step.after)
          rec.url = p.url()
          break
        }
        case 'fill': {
          await locatorFor(p, step).first().fill(step.value)
          break
        }
        case 'press': {
          await p.keyboard.press(step.key)
          if (step.after) await p.waitForTimeout(step.after)
          break
        }
        case 'select': {
          const loc = locatorFor(p, step).first()
          if (step.value !== undefined) await loc.selectOption(step.value)
          else await loc.selectOption({ label: step.label })
          break
        }
        case 'waitFor': {
          await locatorFor(p, step).first().waitFor({ state: step.state || 'visible', timeout: step.timeout })
          break
        }
        case 'wait': {
          await p.waitForTimeout(step.ms)
          break
        }
        case 'waitText': {
          await p.getByText(step.text, { exact: !!step.exact }).first().waitFor({ state: 'visible', timeout: step.timeout })
          break
        }
        case 'text': {
          rec.value = (await locatorFor(p, step).first().innerText()).slice(0, step.max || 4000)
          break
        }
        case 'value': {
          rec.value = await locatorFor(p, step).first().inputValue()
          break
        }
        case 'count': {
          rec.value = await locatorFor(p, step).count()
          break
        }
        case 'visible': {
          rec.value = await locatorFor(p, step).first().isVisible()
          break
        }
        case 'enabled': {
          const loc = locatorFor(p, step).first()
          rec.value = await loc.enabled()
          rec.visible = await loc.isVisible().catch(() => false)
          break
        }
        case 'disabledButtons': {
          rec.value = await p.evaluate(() =>
            Array.from(document.querySelectorAll('button'))
              .filter((b) => b.disabled)
              .map((b) => b.innerText.trim())
          )
          break
        }
        case 'shot': {
          const file = path.resolve(step.path)
          fs.mkdirSync(path.dirname(file), { recursive: true })
          await p.screenshot({ path: file, fullPage: !!step.full })
          rec.value = file
          break
        }
        case 'eval': {
          rec.value = await p.evaluate(step.js)
          break
        }
        case 'table': {
          rec.value = await p.evaluate((sel) => {
            const root = sel ? document.querySelector(sel) : document
            return Array.from(root.querySelectorAll('table tbody tr')).map((tr) =>
              Array.from(tr.querySelectorAll('td')).map((td) => td.innerText.trim().replace(/\s+/g, ' '))
            )
          }, step.sel || null)
          break
        }
        case 'headers': {
          rec.value = await p.evaluate((sel) => {
            const root = sel ? document.querySelector(sel) : document
            return Array.from(root.querySelectorAll('table thead th')).map((th) => th.innerText.trim())
          }, step.sel || null)
          break
        }
        case 'bodyText': {
          rec.value = (await p.locator('body').innerText()).slice(0, step.max || 6000)
          break
        }
        case 'url': {
          rec.value = p.url()
          break
        }
        case 'storage': {
          rec.value = await p.evaluate((k) => localStorage.getItem(k), step.key)
          break
        }
        case 'token': {
          rec.value = await p.evaluate(() => localStorage.getItem('campus_market_token') || sessionStorage.getItem('campus_market_token'))
          break
        }
        case 'upload': {
          await locatorFor(p, step).first().setInputFiles(step.files)
          break
        }
        case 'clickRowBtn': {
          const row = p.locator('tr', { hasText: step.rowText }).first()
          await row.getByRole('button', { name: step.btn, exact: true }).click()
          if (step.after) await p.waitForTimeout(step.after)
          break
        }
        case 'rowBtn': {
          const row = p.locator('tr', { hasText: step.rowText }).first()
          const b = row.getByRole('button', { name: step.btn, exact: true })
          rec.value = { count: await b.count(), disabled: await b.first().isDisabled().catch(() => null) }
          break
        }
        case 'dialogText': {
          rec.value = (await p.locator('[role="dialog"], [role="alertdialog"]').first().innerText()).slice(0, 2000)
          break
        }
        case 'dialogFill': {
          const scope = p.locator('[role="dialog"], [role="alertdialog"]').first()
          if (step.sel) await scope.locator(step.sel).fill(step.value)
          else await scope.locator('textarea, input[type="text"], input:not([type])').first().fill(step.value)
          break
        }
        case 'dialogBtn': {
          const scope = p.locator('[role="dialog"], [role="alertdialog"]').first()
          await scope.getByRole('button', { name: step.name, exact: true }).first().click()
          if (step.after) await p.waitForTimeout(step.after)
          break
        }
        case 'toasts': {
          rec.value = await p.locator('[data-sonner-toast]').allInnerTexts()
          break
        }
        case 'confirmOpen': {
          rec.value = await p.locator('[role="alertdialog"]').count()
          break
        }
        case 'pickOption': {
          if (step.trigger) await p.locator(step.trigger).first().click()
          else await p.getByRole('combobox').nth(step.index || 0).click()
          await p.waitForTimeout(250)
          await p.getByRole('option', { name: step.name, exact: !!step.exact }).first().click()
          if (step.after) await p.waitForTimeout(step.after)
          break
        }
        case 'tab': {
          await p.getByRole('tab', { name: step.name, exact: true }).click()
          if (step.after) await p.waitForTimeout(step.after)
          break
        }
        case 'api': {
          rec.value = await p.evaluate(
            async ({ method, url, body }) => {
              const token =
                localStorage.getItem('campus_market_token') || sessionStorage.getItem('campus_market_token')
              const res = await fetch(url, {
                method: method || 'GET',
                headers: {
                  'Content-Type': 'application/json',
                  ...(token ? { Authorization: `Bearer ${token}` } : {})
                },
                ...(body ? { body: JSON.stringify(body) } : {})
              })
              const json = await res.json().catch(() => null)
              return { status: res.status, code: json && json.code, message: json && json.message, data: json && json.data }
            },
            { method: step.method, url: step.url, body: step.body }
          )
          break
        }
        case 'console': {
          break
        }
        default:
          throw new Error('unknown op ' + step.op)
      }
      rec.ok = true
    } catch (e) {
      rec.ok = false
      rec.error = String(e.message || e).slice(0, 600)
    }
    out.push(rec)
    if (step.stopOnFail && !rec.ok) break
  }

  const result = {
    finalUrl: pages.A ? pages.A.url() : null,
    pages: Object.keys(pages),
    steps: out,
    consoleErrors: logs.A ? logs.A.console.slice(0, 40) : [],
    pageErrors: logs.A ? logs.A.errors.slice(0, 40) : [],
    logsByPage: Object.fromEntries(
      Object.entries(logs).map(([k, v]) => [
        k,
        { console: v.console.slice(0, 20), errors: v.errors.slice(0, 20), ws: v.ws }
      ])
    )
  }
  const text = JSON.stringify(result, null, 1)
  if (reportPath) fs.writeFileSync(reportPath, text)
  console.log(text)
  await browser.close().catch(() => {})
})().catch((e) => {
  console.error('DRIVER FATAL: ' + (e.stack || e))
  process.exit(1)
})
