/**
 * token 存取（与 Vue 版 frontend-web/src/utils/auth.js 语义逐条一致，禁止改动键名与行为）：
 * - 键名：access = `campus_market_token`、refresh = `campus_market_refresh_token`
 * - 默认落 localStorage；`remember === false` 时落 sessionStorage（关闭浏览器即失效）
 * - 写入目标 storage 的同时清除另一侧的同一 key（保证两种持久化不共存）
 * - 读取时 localStorage 优先，回退 sessionStorage
 */
const TOKEN_KEY = 'campus_market_token'
const REFRESH_KEY = 'campus_market_refresh_token'

const pickStorage = (remember?: boolean): Storage => (remember === false ? sessionStorage : localStorage)
const otherStorage = (remember?: boolean): Storage => (remember === false ? localStorage : sessionStorage)

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string, remember = true): void {
  pickStorage(remember).setItem(TOKEN_KEY, token)
  otherStorage(remember).removeItem(TOKEN_KEY)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_KEY) || sessionStorage.getItem(REFRESH_KEY) || ''
}

export function setRefreshToken(token: string, remember = true): void {
  pickStorage(remember).setItem(REFRESH_KEY, token)
  otherStorage(remember).removeItem(REFRESH_KEY)
}

export function clearRefreshToken(): void {
  localStorage.removeItem(REFRESH_KEY)
  sessionStorage.removeItem(REFRESH_KEY)
}
