// 联调：后端接口契约实测校对（只读 + 必要的改密/建号）
const BASE = 'http://localhost:8080'

let token = ''
function log(...a) { console.log(...a) }
function short(v, n = 420) {
  const s = typeof v === 'string' ? v : JSON.stringify(v)
  if (s === undefined) return 'undefined'
  return s.length > n ? s.slice(0, n) + `…(+${s.length - n})` : s
}
function keys(o) { return o && typeof o === 'object' ? Object.keys(o).join(',') : typeof o }

async function call(method, path, { body, token: tk, raw } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (tk) headers['Authorization'] = `Bearer ${tk}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json
  try { json = JSON.parse(text) } catch { json = text }
  if (raw) return { http: res.status, json }
  const code = json && typeof json === 'object' ? json.code : 'n/a'
  const data = json && typeof json === 'object' ? json.data : json
  log(`${method} ${path} -> http=${res.status} code=${code} data=${short(data, 320)}`)
  return { http: res.status, json, data }
}

const results = {}

;(async () => {
  log('===== 1) 登录门禁 =====')
  const l1 = await call('POST', '/api/admin/auth/login', { body: { username: 'superadmin', password: 'Admin@12345' }, raw: true })
  log('login raw:', short(l1.json, 600))
  log('login data keys:', keys(l1.json.data))
  results.login = l1.json.data
  const t1 = l1.json.data.token

  log('--- 未改密时访问受保护端点（应被 40310 门禁拦截）---')
  await call('GET', '/api/admin/dashboard', { token: t1 })
  await call('GET', '/api/admin/users?pageNum=1&pageSize=10', { token: t1 })

  log('===== 2) 强制改密 =====')
  await call('POST', '/api/admin/auth/change-password', { token: t1, body: { oldPassword: 'Admin@12345', newPassword: 'SuperAdmin@2026' } })
  const l2 = await call('POST', '/api/admin/auth/login', { body: { username: 'superadmin', password: 'SuperAdmin@2026' }, raw: true })
  log('relogin raw:', short(l2.json, 600))
  results.relogin = l2.json.data
  token = l2.json.data.token

  log('===== 3) /auth/me =====')
  const me = await call('GET', '/api/admin/auth/me', { token, raw: true })
  log('me raw:', short(me.json, 600))

  log('===== 4) 只读端点契约 =====')
  const dash = await call('GET', '/api/admin/dashboard', { token, raw: true })
  log('dashboard raw:', short(dash.json, 1400))
  results.dashboard = dash.json.data

  for (const t of ['GOODS', 'WANT', 'SWAP']) {
    await call('GET', `/api/admin/contents?targetType=${t}&pageNum=1&pageSize=10`, { token, raw: true })
  }
  const c1 = await call('GET', '/api/admin/contents?targetType=GOODS&pageNum=1&pageSize=10', { token, raw: true })
  log('contents GOODS raw:', short(c1.json, 900))
  const c2 = await call('GET', '/api/admin/contents?targetType=GOODS&status=ON_SALE&keyword=教材&pageNum=1&pageSize=10', { token, raw: true })
  log('contents filtered raw:', short(c2.json, 700))

  await call('GET', '/api/admin/reports?pageNum=1&pageSize=10', { token, raw: true })
  const r1 = await call('GET', '/api/admin/reports?pageNum=1&pageSize=10&status=0', { token, raw: true })
  log('reports pending raw:', short(r1.json, 1200))
  const r2 = await call('GET', '/api/admin/reports/1', { token, raw: true })
  log('report detail raw:', short(r2.json, 900))

  const u1 = await call('GET', '/api/admin/users?pageNum=1&pageSize=10', { token, raw: true })
  log('users raw:', short(u1.json, 1200))
  const u2 = await call('GET', '/api/admin/users/1', { token, raw: true })
  log('user detail raw:', short(u2.json, 700))
  const cr = await call('GET', '/api/admin/users/1/credits?pageNum=1&pageSize=10', { token, raw: true })
  log('credits raw:', short(cr.json, 900))

  const cat = await call('GET', '/api/admin/categories', { token, raw: true })
  log('categories raw:', short(cat.json, 500))
  const tag = await call('GET', '/api/admin/tags', { token, raw: true })
  log('tags raw:', short(tag.json, 400))
  const sw = await call('GET', '/api/admin/sensitive-words?pageNum=1&pageSize=5', { token, raw: true })
  log('sensitive-words raw:', short(sw.json, 700))
  const ban = await call('GET', '/api/admin/banners', { token, raw: true })
  log('banners raw:', short(ban.json, 500))
  const nt = await call('GET', '/api/admin/notices', { token, raw: true })
  log('notices raw:', short(nt.json, 700))
  const lg = await call('GET', '/api/admin/operation-logs?pageNum=1&pageSize=5', { token, raw: true })
  log('logs raw:', short(lg.json, 900))
  const ac = await call('GET', '/api/admin/accounts?pageNum=1&pageSize=10', { token, raw: true })
  log('accounts raw:', short(ac.json, 700))

  log('===== 5) 新建 auditor / operator =====')
  await call('POST', '/api/admin/accounts', { token, body: { username: 'auditor01', password: 'Audit@2026pw', realName: '审核员甲', role: 'auditor' }, raw: true })
  await call('POST', '/api/admin/accounts', { token, body: { username: 'operator01', password: 'Oper@2026pw', realName: '运营员乙', role: 'operator' }, raw: true })

  log('===== 6) RBAC 实测（三个角色逐一探测）=====')
  for (const acc of [['superadmin', 'SuperAdmin@2026'], ['auditor01', 'Audit@2026pw'], ['operator01', 'Oper@2026pw']]) {
    const r = await call('POST', '/api/admin/auth/login', { body: { username: acc[0], password: acc[1] }, raw: true })
    const tok = r.json.data && r.json.data.token
    log(`--- ${acc[0]} role=${r.json.data && r.json.data.role} mustChange=${r.json.data && r.json.data.mustChangePassword} ---`)
    if (!tok) { log('  登录失败:', short(r.json, 300)); continue }
    let tok2 = null
    // 若新号强制改密，先改密再探测
    if (r.json.data.mustChangePassword) {
      await call('POST', '/api/admin/auth/change-password', { token: tok, body: { oldPassword: acc[1], newPassword: acc[1] + 'x' } })
      const r2 = await call('POST', '/api/admin/auth/login', { body: { username: acc[0], password: acc[1] + 'x' }, raw: true })
      log(`  ${acc[0]} 改密后新密码 = ${acc[1]}x`)
      tok2 = r2.json.data.token
    }
    const use = tok2 || tok
    const probes = [
      ['GET', '/api/admin/dashboard'],
      ['GET', '/api/admin/users?pageNum=1&pageSize=5'],
      ['GET', '/api/admin/contents?targetType=GOODS&pageNum=1&pageSize=5'],
      ['GET', '/api/admin/reports?pageNum=1&pageSize=5'],
      ['GET', '/api/admin/categories'],
      ['GET', '/api/admin/sensitive-words?pageNum=1&pageSize=5'],
      ['GET', '/api/admin/banners'],
      ['GET', '/api/admin/operation-logs?pageNum=1&pageSize=5'],
      ['GET', '/api/admin/accounts?pageNum=1&pageSize=5']
    ]
    for (const [m, p] of probes) {
      const res = await fetch(BASE + p, { headers: { Authorization: `Bearer ${use}` } })
      let j = null
      try { j = await res.json() } catch {}
      log(`  ${p} -> http=${res.status} code=${j && j.code} msg=${j && j.message}`)
    }
  }
  log('===== DONE =====')
})().catch((e) => { console.error('FATAL', e) })
