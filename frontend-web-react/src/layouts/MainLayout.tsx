import { useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Bell, ChevronDown, CircleAlert, MessageCircle, Plus, Search, UserRound } from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import {
  clearCredentials,
  selectBanned,
  selectCreditScore,
  selectIsLoggedIn,
  selectUserInfo,
  setProfile
} from '@/features/auth/authSlice'
import { useGetMeQuery, useLogoutMutation } from '@/api/authApi'
import { useUnreadPolling } from '@/features/msg/useUnreadPolling'
import { selectUnreadChat, selectUnreadNotify } from '@/features/msg/msgSlice'
import { Button } from '@/components/ui/button'
import { RouteLink, useRouteNavigate } from '@/components/RouteLink'
import { cn } from '@/lib/utils'

/** 主导航（详情页高亮对应一级项：`/goods/1` → `/goods`） */
const NAV_ITEMS = [
  { to: '/home', label: '首页' },
  { to: '/goods', label: '商品' },
  { to: '/want', label: '求购' },
  { to: '/swap', label: '交换' }
]

const dotText = (n: number) => (n > 99 ? '99+' : String(n))

/**
 * 商城布局（对齐 Vue 版 layouts/MainLayout.vue 的结构、文案与行为）：
 * - 顶栏：Logo（→ 落地页 `/`）、主导航、搜索、发布、双红点、用户下拉
 * - 全局提示条：封禁（error）/ 信用分 <60（warning）——导航下方、页面内容之上
 * - 刷新页面恢复登录态资料（`GET /users/me`）；登录期间 30s 轮询双红点（封禁不轮询）
 *
 * 新增落地页带来的 URL 变化：Logo → `/`（落地页），导航「首页」→ `/home`（商城主页）。
 */
export default function MainLayout() {
  const dispatch = useAppDispatch()
  const navigate = useRouteNavigate()
  const location = useLocation()

  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const userInfo = useAppSelector(selectUserInfo)
  const isBanned = useAppSelector(selectBanned)
  const creditScore = useAppSelector(selectCreditScore)
  const unreadChat = useAppSelector(selectUnreadChat)
  const unreadNotify = useAppSelector(selectUnreadNotify)

  const [searchText, setSearchText] = useState('')
  const [logoutApi] = useLogoutMutation()

  // 刷新页面后恢复用户资料（登录响应只带摘要，creditScore/封禁态来自 /users/me）
  const { data: me } = useGetMeQuery(undefined, { skip: !isLoggedIn })
  useEffect(() => {
    if (me) dispatch(setProfile(me))
  }, [me, dispatch])

  useUnreadPolling()

  const activeMenu = `/${location.pathname.split('/')[1]}`

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = searchText.trim()
    navigate(q ? `/goods?q=${encodeURIComponent(q)}` : '/goods')
  }

  const handleLogout = async () => {
    try {
      await logoutApi().unwrap()
    } catch {
      /* 无状态 JWT：登出接口失败也照常清理本地登录态 */
    } finally {
      dispatch(clearCredentials())
      navigate('/')
    }
  }

  const banText = (() => {
    const base = '账号已被封禁，暂不能发布、互动、下单与发送消息'
    const extra: string[] = []
    if (userInfo?.banReason) extra.push(`原因：${userInfo.banReason}`)
    if (userInfo?.bannedUntil) extra.push(`解封时间：${userInfo.bannedUntil}`)
    return extra.length ? `${base}（${extra.join('；')}）` : base
  })()

  const notice = !isLoggedIn
    ? null
    : isBanned
      ? { type: 'error' as const, text: banText }
      : typeof creditScore === 'number' && creditScore < 60
        ? {
            type: 'warning' as const,
            text: '信用分低于 60，暂不能发布 / 下单 / 应约 / 发起交换；完成交易与获得好评可恢复'
          }
        : null

  return (
    <div className="min-h-screen">
      <header className="topbar-blur topbar-hold sticky top-0 z-20 border-b border-border">
        <div className="mx-auto flex min-h-[68px] max-w-[1240px] flex-wrap items-center gap-[22px] px-5 py-2.5 lg:py-0">
          <RouteLink to="/" className="inline-flex items-center gap-2.5 whitespace-nowrap text-primary">
            <span className="grid size-[34px] place-items-center rounded-[11px_11px_11px_4px] bg-primary font-extrabold text-primary-foreground shadow-sm">
              闲
            </span>
            <span>
              <strong className="block text-[17px] leading-tight">校园淘</strong>
              <small className="mt-0.5 block text-[10px] font-medium tracking-wide text-muted-foreground">
                让闲置回到校园
              </small>
            </span>
          </RouteLink>

          <nav aria-label="主导航" className="flex w-max items-stretch gap-1 max-lg:order-3 max-lg:h-9 max-lg:w-full max-lg:overflow-x-auto lg:h-[68px]">
            {NAV_ITEMS.map((item) => {
              const active = activeMenu === item.to
              return (
                <RouteLink
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'relative inline-flex items-center px-3 text-[13px] font-semibold transition-colors max-lg:px-2.5',
                    active ? 'text-primary' : 'text-muted-foreground hover:text-primary'
                  )}
                >
                  {item.label}
                  <span
                    className={cn(
                      'absolute inset-x-3 bottom-0 h-0.5 rounded-full',
                      active ? 'bg-primary' : 'bg-transparent'
                    )}
                  />
                </RouteLink>
              )
            })}
          </nav>

          <form
            onSubmit={submitSearch}
            className="ml-auto flex h-[38px] max-w-[300px] flex-1 items-center gap-2 rounded-xl border border-border bg-secondary/40 px-3 text-muted-foreground focus-within:border-primary focus-within:bg-card max-lg:order-4 max-lg:max-w-none max-lg:basis-full"
          >
            <Search className="size-4 shrink-0" />
            <input
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              aria-label="搜索商品"
              placeholder="搜一搜教材、耳机或生活好物"
              className="w-full border-0 bg-transparent text-[13px] text-foreground outline-0 placeholder:text-muted-foreground/70"
            />
          </form>

          <div className="flex items-center gap-2 whitespace-nowrap">
            <Button onClick={() => navigate('/publish')} className="max-lg:px-2.5">
              <Plus className="size-4" />
              发布
            </Button>

            {isLoggedIn ? (
              <>
                <button
                  type="button"
                  aria-label="通知"
                  onClick={() => navigate('/message?tab=notify')}
                  className="relative grid size-[34px] place-items-center rounded-[9px] text-muted-foreground transition hover:bg-secondary/70 hover:text-primary"
                >
                  <Bell className="size-[17px]" />
                  {unreadNotify ? (
                    <span className="absolute -top-0.5 -right-1 min-w-4 rounded-full border-2 border-card bg-[var(--terracotta)] px-1 text-[9px] leading-3 text-white">
                      {dotText(unreadNotify)}
                    </span>
                  ) : null}
                </button>
                <button
                  type="button"
                  aria-label="消息"
                  onClick={() => navigate('/message')}
                  className="relative grid size-[34px] place-items-center rounded-[9px] text-muted-foreground transition hover:bg-secondary/70 hover:text-primary"
                >
                  <MessageCircle className="size-[17px]" />
                  {unreadChat ? (
                    <span className="absolute -top-0.5 -right-1 min-w-4 rounded-full border-2 border-card bg-[var(--terracotta)] px-1 text-[9px] leading-3 text-white">
                      {dotText(unreadChat)}
                    </span>
                  ) : null}
                </button>

                <details className="group relative">
                  <summary className="inline-flex cursor-pointer list-none items-center gap-1.5 text-foreground marker:content-none max-lg:max-w-[84px] max-lg:overflow-hidden max-lg:text-ellipsis">
                    <UserRound className="size-4" />
                    <span className="truncate">{userInfo?.nickname || '我的'}</span>
                    <ChevronDown className="size-3.5" />
                  </summary>
                  <div className="paper-card absolute top-[calc(100%+12px)] right-0 z-30 grid w-[148px] gap-0.5 p-1.5">
                    <button
                      type="button"
                      className="rounded-md px-2.5 py-2 text-left text-xs text-foreground transition hover:bg-secondary/70 hover:text-primary"
                      onClick={() => navigate('/profile')}
                    >
                      个人中心
                    </button>
                    <button
                      type="button"
                      className="rounded-md px-2.5 py-2 text-left text-xs text-foreground transition hover:bg-secondary/70 hover:text-primary"
                      onClick={() => navigate('/orders')}
                    >
                      我的订单
                    </button>
                    <button
                      type="button"
                      className="rounded-md px-2.5 py-2 text-left text-xs text-foreground transition hover:bg-secondary/70 hover:text-primary"
                      onClick={() => navigate('/message')}
                    >
                      消息中心
                    </button>
                    <button
                      type="button"
                      className="mt-0.5 rounded-md border-t border-border px-2.5 py-2 text-left text-xs text-destructive transition hover:bg-destructive/10"
                      onClick={handleLogout}
                    >
                      退出登录
                    </button>
                  </div>
                </details>
              </>
            ) : (
              <>
                <Button variant="ghost" onClick={() => navigate('/login')}>
                  登录
                </Button>
                <Button variant="outline" onClick={() => navigate('/register')}>
                  注册
                </Button>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="route-page mx-auto min-h-[calc(100vh-124px)] w-full max-w-[1200px]">
        {notice ? (
          <div
            className={cn(
              'flex items-center gap-2 px-5 pt-3 text-[13px]',
              notice.type === 'error' ? 'text-destructive' : 'text-[var(--warning)]'
            )}
          >
            <CircleAlert className="size-4 shrink-0" />
            {notice.text}
          </div>
        ) : null}
        <Outlet />
      </main>

      <footer className="min-h-14 px-5 py-[18px] text-center text-[12px] text-muted-foreground">
        校园二手交易系统 · 线下面交 · 平台不经手资金
      </footer>
    </div>
  )
}
