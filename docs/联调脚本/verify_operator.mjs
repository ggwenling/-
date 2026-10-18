// operator 角色 RBAC + 上传接口实测
const BASE = 'http://localhost:8080'
const log = (...a) => console.log(...a)

async function login(u, p) {
  const r = await fetch(BASE + '/api/admin/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: u, password: p })
  })
  return r.json()
}

;(async () => {
  const l = await login('operator01', 'Oper@2026pw')
  log('operator login:', JSON.stringify(l).slice(0, 400))
  let tok = l.data && l.data.token
  if (l.data && l.data.mustChangePassword && tok) {
    const cp = await fetch(BASE + '/api/admin/auth/change-password', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tok}` },
      body: JSON.stringify({ oldPassword: 'Oper@2026pw', newPassword: 'Oper@2026pwx' })
    })
    log('change-password:', JSON.stringify(await cp.json()))
    const l2 = await login('operator01', 'Oper@2026pwx')
    tok = l2.data && l2.data.token
    log('relogin ok:', !!tok)
  }
  const probes = [
    '/api/admin/dashboard',
    '/api/admin/users?pageNum=1&pageSize=5',
    '/api/admin/contents?targetType=GOODS&pageNum=1&pageSize=5',
    '/api/admin/reports?pageNum=1&pageSize=5',
    '/api/admin/categories',
    '/api/admin/tags',
    '/api/admin/sensitive-words?pageNum=1&pageSize=5',
    '/api/admin/banners',
    '/api/admin/notices',
    '/api/admin/operation-logs?pageNum=1&pageSize=5',
    '/api/admin/accounts?pageNum=1&pageSize=5'
  ]
  for (const p of probes) {
    const res = await fetch(BASE + p, { headers: { Authorization: `Bearer ${tok}` } })
    let j = null
    try { j = await res.json() } catch {}
    log(`  ${p} -> http=${res.status} code=${j && j.code} msg=${j && j.message}`)
  }
  log('--- 越权写操作（应为 40300）---')
  for (const [m, p, b] of [
    ['POST', '/api/admin/users/1/credit', { change: 5, remark: '越权测试' }],
    ['POST', '/api/admin/contents/GOODS/1/take-down', { reason: '越权测试' }],
    ['POST', '/api/admin/reports/1/reject', { result: '越权测试' }]
  ]) {
    const res = await fetch(BASE + p, { method: m, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tok}` }, body: JSON.stringify(b) })
    let j = null
    try { j = await res.json() } catch {}
    log(`  ${m} ${p} -> http=${res.status} code=${j && j.code} msg=${j && j.message}`)
  }
  log('done')
})().catch((e) => console.error('FATAL', e))
