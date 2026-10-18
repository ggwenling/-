/**
 * Playwright-CDP 驱动：连接已在 9222 端口运行的无头 Chrome，按 JSON 剧本执行动作并输出结构化结果。
 * 用法： node pw_driver.cjs <actions.json> [--report out.json]
 * 依赖： 全局 playwright（NODE_PATH=C:\Users\31180\AppData\Roaming\npm\node_modules）
 */
const fs = require('fs')
const path = require('path')
const { chromium } = require('playwright')

const CDP = process.env.PW_CDP || 'http://127.0.0.1:9222'
const actionsFile = process.argv[2]
if (!actionsFile) {
  console.error('usage: node pw_driver.cjs <actions.json> [--report out.json]')
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
  const ctx = browser.contexts()[0]
  const page = ctx.pages()[0] || (await ctx.newPage())
  page.setDefaultTimeout(Number(process.env.PW_TIMEOUT || 20000))

  const consoleMsgs = []
  const pageErrors = []
  page.on('console', (m) => {
    if (['error', 'warning'].includes(m.type())) consoleMsgs.push(`${m.type()}: ${m.text()}`.slice(0, 500))
  })
  page.on('pageerror', (e) => pageErrors.push(String(e).slice(0, 500)))
  page.on('requestfailed', (r) => pageErrors.push(`REQFAIL ${r.method()} ${r.url()} ${r.failure() && r.failure().errorText}`))

  const out = []
  for (const step of actions) {
    const rec = { op: step.op, note: step.note }
    try {
      switch (step.op) {
        case 'goto': {
          await page.goto(step.url, { waitUntil: step.waitUntil || 'domcontentloaded' })
          if (step.waitFor) await locatorFor(page, step.waitFor).first().waitFor({ state: 'visible' })
          rec.url = page.url()
          break
        }
        case 'reload': {
          await page.reload({ waitUntil: 'domcontentloaded' })
          rec.url = page.url()
          break
        }
        case 'click': {
          const loc = locatorFor(page, step).first()
          await loc.click({ force: !!step.force })
          if (step.after) await page.waitForTimeout(step.after)
          rec.url = page.url()
          break
        }
        case 'fill': {
          await locatorFor(page, step).first().fill(step.value)
          break
        }
        case 'press': {
          await page.keyboard.press(step.key)
          if (step.after) await page.waitForTimeout(step.after)
          break
        }
        case 'select': {
          const loc = locatorFor(page, step).first()
          if (step.label !== undefined && !step.sel && !step.testid && !step.role) {
            await loc.selectOption({ label: step.label })
          } else if (step.value !== undefined) {
            await loc.selectOption(step.value)
          } else {
            await loc.selectOption({ label: step.label })
          }
          break
        }
        case 'waitFor': {
          await locatorFor(page, step).first().waitFor({ state: step.state || 'visible', timeout: step.timeout })
          break
        }
        case 'wait': {
          await page.waitForTimeout(step.ms)
          break
        }
        case 'waitText': {
          await page.getByText(step.text, { exact: !!step.exact }).first().waitFor({ state: 'visible', timeout: step.timeout })
          break
        }
        case 'text': {
          rec.value = (await locatorFor(page, step).first().innerText()).slice(0, step.max || 4000)
          break
        }
        case 'value': {
          rec.value = await locatorFor(page, step).first().inputValue()
          break
        }
        case 'count': {
          rec.value = await locatorFor(page, step).count()
          break
        }
        case 'visible': {
          rec.value = await locatorFor(page, step).first().isVisible()
          break
        }
        case 'enabled': {
          const loc = locatorFor(page, step).first()
          rec.value = await loc.enabled()
          rec.visible = await loc.isVisible().catch(() => false)
          break
        }
        case 'disabledButtons': {
          rec.value = await page.evaluate(() =>
            Array.from(document.querySelectorAll('button'))
              .filter((b) => b.disabled)
              .map((b) => b.innerText.trim())
          )
          break
        }
        case 'shot': {
          const p = path.resolve(step.path)
          fs.mkdirSync(path.dirname(p), { recursive: true })
          await page.screenshot({ path: p, fullPage: !!step.full })
          rec.value = p
          break
        }
        case 'eval': {
          rec.value = await page.evaluate(step.js)
          break
        }
        case 'table': {
          rec.value = await page.evaluate((sel) => {
            const root = sel ? document.querySelector(sel) : document
            return Array.from(root.querySelectorAll('table tbody tr')).map((tr) =>
              Array.from(tr.querySelectorAll('td')).map((td) => td.innerText.trim().replace(/\s+/g, ' '))
            )
          }, step.sel || null)
          break
        }
        case 'headers': {
          rec.value = await page.evaluate((sel) => {
            const root = sel ? document.querySelector(sel) : document
            return Array.from(root.querySelectorAll('table thead th')).map((th) => th.innerText.trim())
          }, step.sel || null)
          break
        }
        case 'bodyText': {
          rec.value = (await page.locator('body').innerText()).slice(0, step.max || 6000)
          break
        }
        case 'url': {
          rec.value = page.url()
          break
        }
        case 'storage': {
          rec.value = await page.evaluate((k) => localStorage.getItem(k), step.key)
          break
        }
        case 'upload': {
          await locatorFor(page, step).first().setInputFiles(step.files)
          break
        }
        case 'clickRowBtn': {
          const row = page.locator('tr', { hasText: step.rowText }).first()
          await row.getByRole('button', { name: step.btn, exact: true }).click()
          if (step.after) await page.waitForTimeout(step.after)
          break
        }
        case 'rowBtn': {
          const row = page.locator('tr', { hasText: step.rowText }).first()
          const b = row.getByRole('button', { name: step.btn, exact: true })
          rec.value = { count: await b.count(), disabled: await b.first().isDisabled().catch(() => null) }
          break
        }
        case 'dialogText': {
          rec.value = (await page.locator('[role="dialog"], [role="alertdialog"]').first().innerText()).slice(0, 2000)
          break
        }
        case 'dialogFill': {
          const scope = page.locator('[role="dialog"], [role="alertdialog"]').first()
          if (step.sel) await scope.locator(step.sel).fill(step.value)
          else await scope.locator('textarea, input[type="text"], input:not([type])').first().fill(step.value)
          break
        }
        case 'dialogBtn': {
          const scope = page.locator('[role="dialog"], [role="alertdialog"]').first()
          await scope.getByRole('button', { name: step.name, exact: true }).first().click()
          if (step.after) await page.waitForTimeout(step.after)
          break
        }
        case 'toasts': {
          rec.value = await page.locator('[data-sonner-toast]').allInnerTexts()
          break
        }
        case 'confirmOpen': {
          rec.value = await page.locator('[role="alertdialog"]').count()
          break
        }
        case 'pickOption': {
          // Radix Select：点触发器后点选项
          if (step.trigger) await page.locator(step.trigger).first().click()
          else await page.getByRole('combobox').nth(step.index || 0).click()
          await page.waitForTimeout(250)
          await page.getByRole('option', { name: step.name, exact: !!step.exact }).first().click()
          if (step.after) await page.waitForTimeout(step.after)
          break
        }
        case 'tab': {
          await page.getByRole('tab', { name: step.name, exact: true }).click()
          if (step.after) await page.waitForTimeout(step.after)
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
    finalUrl: page.url(),
    steps: out,
    consoleErrors: consoleMsgs.slice(0, 40),
    pageErrors: pageErrors.slice(0, 40)
  }
  const text = JSON.stringify(result, null, 1)
  if (reportPath) fs.writeFileSync(reportPath, text)
  console.log(text)
  await browser.close().catch(() => {})
})().catch((e) => {
  console.error('DRIVER FATAL: ' + (e.stack || e))
  process.exit(1)
})
