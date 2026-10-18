import type { BaseQueryFn } from '@reduxjs/toolkit/query'
import { AxiosError, type AxiosRequestConfig } from 'axios'
import { toast } from 'sonner'
import { http } from './request'
import { clearCredentials } from '@/features/auth/authSlice'
import { AUTH_EXPIRED_CODES, CODE_BANNED, CODE_RESTRICTED } from '@/lib/constants'
import { showBannedDialog } from '@/components/BannedDialog'
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

/**
 * 登录态失效时的导航回调。
 * 这里刻意**不直接 import router**：Vue 版存在
 * `router → stores/user → api/user → api/request → router` 循环依赖，React 版用注入回调规避。
 * 由 main.tsx 在创建 router 后注入。
 */
let unauthorizedHandler: (() => void) | null = null
export const setUnauthorizedHandler = (fn: () => void): void => {
  unauthorizedHandler = fn
}

/**
 * axios 版 baseQuery：统一解包 `{code,message,data}` —— 成功时**返回 data 本身**（不是整个信封），
 * 因此所有调用点直接使用 payload（与 Vue 版 `res.data.x` 的写法成对改写）。
 *
 * 业务码语义与 Vue 版 frontend-web/src/api/request.js 逐条等价：
 * - `40100/40101`：清空登录态 → 跳 `/login?redirect=<当前地址>` → 提示一次
 * - `40301` 封禁：弹**去重**确认框（不登出，封禁用户仍可查看原因/期限）
 * - `40302` 受限：warning 提示 + 恢复指引
 * - 其它非 0 / 网络异常：error 提示一次
 * 所有失败**只提示一次**（Vue 版由拦截器统一提示，调用方的 catch 只做跳转）。
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
          unauthorizedHandler?.()
          toast.error(r.message || '登录已过期，请重新登录')
          return { error: { status: r.code, code: r.code, message: r.message || '登录已过期', data: r } }
        }
        if (r.code === CODE_BANNED) {
          // 封禁：弹窗即提示，不再额外 toast（Vue 版同口径）
          showBannedDialog(r.message)
          return { error: { status: r.code, code: r.code, message: r.message || '账号已被封禁', data: r } }
        }
        if (r.code === CODE_RESTRICTED) {
          toast.warning(`${r.message || '信用分受限'}（完成订单可获得信用分，恢复至 60 分后自动解除）`)
          return { error: { status: r.code, code: r.code, message: r.message || '信用分受限', data: r } }
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
    // REST 接口业务失败恒为 HTTP 200，这里的 401 只可能来自网络层/网关
    if (status === 401) {
      api.dispatch(clearCredentials())
      unauthorizedHandler?.()
      toast.error('登录已过期，请重新登录')
      return { error: { status: 401, message: '登录已过期，请重新登录' } }
    }
    const message = axiosErr.message || '网络异常，请稍后重试'
    toast.error(message)
    return { error: { status: 'FETCH_ERROR', message } }
  }
}

const SUCCESS = 0
