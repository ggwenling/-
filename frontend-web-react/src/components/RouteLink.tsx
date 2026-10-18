import { useCallback } from 'react'
import {
  Link,
  useLocation,
  useNavigate,
  type LinkProps,
  type NavigateFunction,
  type NavigateOptions,
  type To
} from 'react-router-dom'

/**
 * 跨页面过场的统一入口（View Transitions）。
 *
 * 设计约束（与 `docs` 里的过渡方案一致）：
 * - **只有 pathname 变化才过渡**；`?pageNum=`、`?categoryId=`、`?tab=` 这类同页查询变化
 *   一律不启用，否则每翻一页整屏都会闪一下。
 * - 浏览器不支持 `document.startViewTransition` 时返回 `false`，React Router 会忽略该标志，
 *   由 `index.css` 里 `@supports not (view-transition-name: none)` 分支的入场动画兜底。
 * - 这里不含任何业务判断，只做「这个链接要不要过场」这一件事。
 */

export const supportsViewTransition =
  typeof document !== 'undefined' && typeof document.startViewTransition === 'function'

/** 首屏空闲时预热的主路由 chunk（避免首次进入时快照拍到骨架屏） */
const PREFETCH_ROUTES: Record<string, () => Promise<unknown>> = {
  '/home': () => import('@/pages/home/HomePage'),
  '/goods': () => import('@/pages/goods/GoodsListPage'),
  '/want': () => import('@/pages/want/WantSquarePage'),
  '/swap': () => import('@/pages/swap/SwapSquarePage'),
  '/login': () => import('@/pages/auth/LoginPage')
}

const warmed = new Set<string>()

/** 按路径预热目标路由的 chunk（重复调用只触发一次，失败会释放以便重试） */
export function prefetchRoute(to: string) {
  if (!to || /^[a-z]+:/i.test(to) || to.startsWith('#')) return
  const key = Object.keys(PREFETCH_ROUTES).find((path) => to === path || to.startsWith(`${path}/`) || to.startsWith(`${path}?`))
  if (!key || warmed.has(key)) return
  warmed.add(key)
  void PREFETCH_ROUTES[key]().catch(() => warmed.delete(key))
}

/** `to` 是否指向另一个 pathname（查询参数变化不算换页） */
function isCrossPage(to: To | number, currentPathname: string): boolean {
  if (typeof to === 'number') return true
  const raw = typeof to === 'string' ? to : `${to.pathname ?? ''}${to.search ?? ''}`
  if (!raw || /^[a-z]+:/i.test(raw) || raw.startsWith('#')) return false
  try {
    return new URL(raw, 'http://local').pathname !== currentPathname
  } catch {
    return true
  }
}

/**
 * `useNavigate()` 的替代品：跨页跳转自动带过场，同页查询变化保持原样。
 * 签名与 `useNavigate` 完全一致，逐处替换即可。
 */
export function useRouteNavigate(): NavigateFunction {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return useCallback(
    (to: To | number, options?: NavigateOptions) => {
      if (typeof to === 'number') {
        navigate(to)
        return
      }
      if (!supportsViewTransition) {
        navigate(to, options)
        return
      }
      prefetchRoute(typeof to === 'string' ? to : `${to.pathname ?? ''}${to.search ?? ''}`)
      navigate(to, { ...options, viewTransition: isCrossPage(to, pathname) })
    },
    [navigate, pathname]
  )
}

/**
 * `<Link>` 的替代品：按同一条规则决定是否过场，并在 hover/聚焦时预热目标 chunk。
 * 其余 props 原样透传给 `<Link>`（外部链接、新标签、hash 锚点行为不变）。
 */
export function RouteLink({ to, viewTransition, onMouseEnter, onFocus, ...rest }: LinkProps) {
  const { pathname } = useLocation()
  const vt = viewTransition ?? (supportsViewTransition && isCrossPage(to, pathname))

  const warm = () => {
    if (typeof to === 'string') prefetchRoute(to)
  }

  return (
    <Link
      to={to}
      viewTransition={vt}
      onMouseEnter={(event) => {
        warm()
        onMouseEnter?.(event)
      }}
      onFocus={(event) => {
        warm()
        onFocus?.(event)
      }}
      {...rest}
    />
  )
}

export default RouteLink
