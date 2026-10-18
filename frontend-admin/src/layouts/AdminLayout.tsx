import { Suspense, useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Copy, LogOut, Store, UserRound } from 'lucide-react'
import { toast } from 'sonner'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { clearCredentials, selectAdmin } from '@/features/auth/authSlice'
import { useLogoutMutation } from '@/api/authApi'
import { NAV_ITEMS, navItemByPath } from '@/router/nav'
import { ROLE_META } from '@/lib/constants'
import { PageLoader } from '@/components/PageLoader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'

export function AdminLayout() {
  const admin = useAppSelector(selectAdmin)
  const role = admin?.role
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const [logoutApi, { isLoading: loggingOut }] = useLogoutMutation()

  const allowedMenus = NAV_ITEMS.filter((item) => item.roles.includes(role as never))
  const current = navItemByPath(location.pathname)
  const roleMeta = role ? ROLE_META[role] : null

  // 路由标题同步到浏览器标签
  useEffect(() => {
    document.title = current ? `${current.title} · 校园二手管理后台` : '校园二手管理后台'
  }, [current])

  const handleLogout = async () => {
    try {
      await logoutApi().unwrap()
    } catch {
      // 无状态 JWT：即便登出接口失败，也照常清理本地登录态
    } finally {
      dispatch(clearCredentials())
      toast.success('已退出登录')
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="flex min-h-screen w-full bg-background">
      {/* ==================== 侧栏 ==================== */}
      <aside className="relative z-10 hidden w-[248px] shrink-0 flex-col border-r border-border bg-[#fbf7ee] md:flex">
        <div className="flex items-center gap-3 px-5 py-5">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-xs">
            <Store className="size-5" />
          </div>
          <div className="leading-tight">
            <div className="text-sm font-bold text-foreground">校园二手</div>
            <div className="text-[11px] tracking-wide text-muted-foreground">管理后台 · ADMIN</div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 pb-4">
          {allowedMenus.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  cn(
                    'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground shadow-xs'
                      : 'text-foreground/75 hover:bg-secondary hover:text-foreground'
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className={cn('size-4 shrink-0', isActive ? 'text-primary-foreground' : 'text-muted-foreground group-hover:text-primary')} />
                    <span className="flex-1 truncate">{item.title}</span>
                  </>
                )}
              </NavLink>
            )
          })}
        </nav>

        <div className="px-5 pb-5">
          <div className="dashed-card px-3 py-3 text-center">
            <span className="stamp-badge">校园生活广场</span>
            <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
              公告 / 处置 / 词库
              <br />
              一站式运营工作台
            </p>
          </div>
        </div>
      </aside>

      {/* ==================== 主区 ==================== */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="topbar-blur sticky top-0 z-20 flex h-16 items-center justify-between gap-4 border-b border-border px-5">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span>管理后台</span>
              <span className="text-border">/</span>
              <span className="text-foreground/80">{current?.title || '工作台'}</span>
            </div>
            <h2 className="truncate text-sm font-semibold text-foreground">{current?.desc || '校园二手交易平台运营管理'}</h2>
          </div>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="h-10 gap-2 px-2">
                <span className="flex size-8 items-center justify-center rounded-full bg-secondary text-primary">
                  <UserRound className="size-4" />
                </span>
                <span className="hidden text-left leading-tight sm:block">
                  <span className="block text-sm font-semibold text-foreground">
                    {admin?.realName || admin?.username || '管理员'}
                  </span>
                  <span className="block text-[11px] text-muted-foreground">{roleMeta?.label || '未登录'}</span>
                </span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel className="flex items-center justify-between gap-2">
                <span className="truncate">{admin?.username}</span>
                {roleMeta ? <Badge variant="soft">{roleMeta.label}</Badge> : null}
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onSelect={() => {
                  navigator.clipboard?.writeText(String(admin?.id ?? '')).then(
                    () => toast.success('已复制管理员 ID'),
                    () => toast.error('复制失败')
                  )
                }}
              >
                <Copy />
                复制管理员 ID
              </DropdownMenuItem>
              <DropdownMenuItem variant="destructive" disabled={loggingOut} onSelect={handleLogout}>
                <LogOut />
                退出登录
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </header>

        <main className="relative z-[1] flex-1 p-5">
          <Suspense fallback={<PageLoader />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </div>
  )
}
