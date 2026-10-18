import axios from 'axios'
import { getToken } from '@/features/auth/authSlice'

/**
 * 后台独立实例：baseURL /api/admin（PRD ADM-01 后台独立登录）。
 * 响应解包与错误提示统一在 RTK Query 的 baseQuery 完成，这里只注入鉴权头。
 */
export const http = axios.create({
  baseURL: '/api/admin',
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
