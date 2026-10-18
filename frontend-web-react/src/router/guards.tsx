import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn } from '@/features/auth/authSlice'

/**
 * 登录守卫：与 Vue 版 `router.beforeEach` 等价 —— **只校验"是否登录"**。
 *
 * 校园认证状态 / 信用受限 / 封禁的细粒度拦截**不在前端守卫**，由后端返回码驱动
 * （40302 受限 → warning 提示；40301 封禁 → 弹窗告知），见 `api/baseQuery.ts`。
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const location = useLocation()

  if (!isLoggedIn) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }

  return <>{children}</>
}
