import type { BaseQueryFn } from '@reduxjs/toolkit/query'
import { AxiosError, type AxiosRequestConfig } from 'axios'
import { toast } from 'sonner'
import { http } from './request'
import { clearCredentials } from '@/features/auth/authSlice'
import type { Result } from './types'

export interface AxiosBaseQueryArgs {
  url: string
  method?: AxiosRequestConfig['method']
  data?: unknown
  params?: unknown
  headers?: Record<string, string>
}

export interface AxiosBaseQueryError {
  status: number | string
  message: string
  code?: number
  data?: unknown
}

/** 登录态失效码（与后端 ErrorCode 对齐） */
const AUTH_EXPIRED_CODES = [40100, 40101]

/**
 * axios 版 baseQuery：统一解包 {code,message,data}，
 * code!==0 → 返回 error（并 toast 提示），40100/40101 → 清空登录态。
 * 语义与旧版 frontend-admin 的 axios 拦截器完全一致。
 */
export const axiosBaseQuery: BaseQueryFn<AxiosBaseQueryArgs, unknown, AxiosBaseQueryError> = async (
  args,
  api
) => {
  try {
    const result = await http({
      url: args.url,
      method: args.method ?? 'GET',
      data: args.data,
      params: args.params,
      headers: args.headers
    })

    const res = result.data as Result<unknown> | unknown
    if (res && typeof res === 'object' && 'code' in (res as Record<string, unknown>)) {
      const r = res as Result<unknown>
      if (r.code !== 0) {
        if (AUTH_EXPIRED_CODES.includes(r.code)) {
          api.dispatch(clearCredentials())
        }
        toast.error(r.message || '请求失败')
        return { error: { status: r.code, code: r.code, message: r.message || '请求失败', data: r } }
      }
      return { data: r.data }
    }
    return { data: res }
  } catch (err) {
    const axiosErr = err as AxiosError
    const status = axiosErr.response?.status
    if (status === 401) {
      api.dispatch(clearCredentials())
      toast.error('登录已过期，请重新登录')
      return { error: { status: 401, message: '登录已过期，请重新登录' } }
    }
    const message = axiosErr.message || '网络异常，请稍后重试'
    toast.error(message)
    return { error: { status: 'FETCH_ERROR', message } }
  }
}
