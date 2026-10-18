import axios from 'axios'
import { getToken } from '@/lib/auth-storage'

/**
 * 前台 axios 实例：baseURL `/api`（与 Vue 版 frontend-web/src/api/request.js 一致）。
 * 响应解包、业务码分支与提示统一在 RTK Query 的 baseQuery 完成（见 api/baseQuery.ts），
 * 这里只做 baseURL / 超时 / 鉴权头注入。
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default http
