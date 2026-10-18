import { Suspense, useEffect } from 'react'
import { Outlet, ScrollRestoration, useLocation, useMatches, useNavigationType } from 'react-router-dom'
import { PageLoader } from '@/components/PageLoader'
import { prefetchRoute, supportsViewTransition } from '@/components/RouteLink'

/** 首屏之后预热的主路由（与 RouteLink 里的 PREFETCH_ROUTES 对应） */
const WARM_ROUTES = ['/home', '/goods', '/want', '/swap', '/login']

/**
 * 路由根壳：负责 `document.title` 同步（与 Vue 版 router.afterEach 等价）、顶层 Suspense、
 * 滚动复位，以及路由过场的两种「挂点」：
 *
 * - 支持 View Transitions 时：什么都不额外包，靠 `index.css` 里的 `::view-transition-*` 补间；
 *   这里只把 `navigationType` 写到 `<html data-nav>`，供 CSS 区分前进/后退的位移方向。
 * - 不支持时：用 `key={pathname}` 强制重挂 + `.route-fallback` 做入场动画（同一个 keyframes）。
 *
 * 另外在首屏空闲时预热主路由 chunk，避免「首次进入某页 → 过渡快照拍到骨架屏」。
 */
export function RootShell() {
  const matches = useMatches()
  const { pathname } = useLocation()
  const navigationType = useNavigationType()

  useEffect(() => {
    const withTitle = [...matches].reverse().find((m) => (m.handle as { title?: string } | undefined)?.title)
    const title = (withTitle?.handle as { title?: string } | undefined)?.title
    document.title = title ? `${title} · 校园二手交易系统` : '校园二手交易系统'
  }, [matches])

  // 前进/后退方向感：只影响 CSS 里 vt-in / vt-in-back 的取值
  useEffect(() => {
    document.documentElement.dataset.nav = navigationType === 'POP' ? 'pop' : 'push'
  }, [navigationType])

  // 首屏空闲预热（不阻塞首渲染，失败静默）
  useEffect(() => {
    const warm = () => WARM_ROUTES.forEach(prefetchRoute)
    const ric = (window as Window & { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number })
      .requestIdleCallback
    if (ric) {
      const id = ric(warm, { timeout: 2000 })
      return () => (window as Window & { cancelIdleCallback?: (id: number) => void }).cancelIdleCallback?.(id)
    }
    const timer = window.setTimeout(warm, 1200)
    return () => window.clearTimeout(timer)
  }, [])

  const content = <Outlet />

  return (
    <>
      <Suspense fallback={<PageLoader />}>
        {supportsViewTransition ? content : (
          <div key={pathname} className="route-fallback">
            {content}
          </div>
        )}
      </Suspense>
      <ScrollRestoration />
    </>
  )
}
