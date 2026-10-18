import { useEffect, useRef, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { toast } from 'sonner'
import { Loader2 } from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { clearCredentials, selectAdmin, selectRole, selectToken, setAdmin } from '@/features/auth/authSlice'
import { useGetMeQuery } from '@/api/authApi'
import type { AdminRole } from '@/api/types'

function FullScreenLoader() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 text-muted-foreground">
      <Loader2 className="size-6 animate-spin text-primary" />
      <span className="text-sm">正在恢复会话…</span>
    </div>
  )
}

/** 登录守卫：未登录跳登录页；已登录但内存无角色则先拉 /auth/me 恢复（消除刷新后的重定向环） */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAppSelector(selectToken)
  const admin = useAppSelector(selectAdmin)
  const dispatch = useAppDispatch()
  const location = useLocation()

  const { data, isError } = useGetMeQuery(undefined, { skip: !token || !!admin })

  useEffect(() => {
    if (data) dispatch(setAdmin(data))
  }, [data, dispatch])

  useEffect(() => {
    if (isError) dispatch(clearCredentials())
  }, [isError, dispatch])

  const mustChangePassword = !!admin?.mustChangePassword

  useEffect(() => {
    if (mustChangePassword) toast.warning('请先完成首次登录强制改密')
  }, [mustChangePassword])

  if (!token) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }

  if (!admin) return <FullScreenLoader />

  // T12 强改密门禁：刷新后内存标记仍在时不得直接进入后台（否则所有请求都会被后端 40310 拦截）
  if (mustChangePassword) return <Navigate to="/login" replace />

  return <>{children}</>
}

/** 角色守卫：meta.roles 不含当前角色时提示并回数据大屏（真正的权限边界是后端 @RequireRole） */
export function RequireRole({ roles, children }: { roles: AdminRole[]; children: ReactNode }) {
  const role = useAppSelector(selectRole)
  const warned = useRef(false)

  useEffect(() => {
    if (!warned.current && role && !roles.includes(role)) {
      warned.current = true
      toast.warning('当前角色无权访问该页面')
    }
  }, [role, roles])

  if (!roles.includes(role as AdminRole)) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}
