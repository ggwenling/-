import { lazy } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { RootShell } from './RootShell'
import { RequireAuth } from './guards'

/**
 * 路由表（与 Vue 版 frontend-web/src/router/index.js 对齐，含新增落地页）。
 *
 * URL 契约（改动会破坏外部链接，禁止改）：
 * - `/`           落地页（**新增**，原为商城主页）
 * - `/home`       商城主页（由 `/` 迁移而来）
 * - `/login`      整屏登录页（不在 MainLayout 内）
 * - `/register`   注册（在 MainLayout 内，公开）
 * - `/verify`     校园认证（在 MainLayout 内，需登录）
 * - 详情页：`/goods/:id`、`/want/:id`、`/swap/:id`、`/orders/:id`
 * - 编辑态复用发布路由 + `?id=`（`isEdit = !!searchParams.get('id')`）
 * - 脏 URL 兜底：`*` → `/home`
 *
 * 路由顺序敏感：`want/publish`、`swap/publish` 必须排在 `want/:id`、`swap/:id` 之前（M4 落地时注意）。
 */
const LandingPage = lazy(() => import('@/pages/landing/LandingPage'))
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'))
const MainLayout = lazy(() => import('@/layouts/MainLayout'))
const HomePage = lazy(() => import('@/pages/home/HomePage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const VerifyPage = lazy(() => import('@/pages/auth/VerifyPage'))
const GoodsListPage = lazy(() => import('@/pages/goods/GoodsListPage'))
const GoodsDetailPage = lazy(() => import('@/pages/goods/GoodsDetailPage'))
const GoodsPublishPage = lazy(() => import('@/pages/goods/GoodsPublishPage'))
const WantSquarePage = lazy(() => import('@/pages/want/WantSquarePage'))
const WantDetailPage = lazy(() => import('@/pages/want/WantDetailPage'))
const WantPublishPage = lazy(() => import('@/pages/want/WantPublishPage'))
const SwapSquarePage = lazy(() => import('@/pages/swap/SwapSquarePage'))
const SwapDetailPage = lazy(() => import('@/pages/swap/SwapDetailPage'))
const SwapPublishPage = lazy(() => import('@/pages/swap/SwapPublishPage'))
const OrderListPage = lazy(() => import('@/pages/order/OrderListPage'))
const OrderDetailPage = lazy(() => import('@/pages/order/OrderDetailPage'))
const MessageCenterPage = lazy(() => import('@/pages/message/MessageCenterPage'))
const ProfilePage = lazy(() => import('@/pages/profile/ProfilePage'))

export const router = createBrowserRouter([
  {
    element: <RootShell />,
    children: [
      { path: '/', element: <LandingPage />, handle: { title: '首页' } },
      { path: '/login', element: <LoginPage />, handle: { title: '登录' } },
      {
        element: <MainLayout />,
        children: [
          { path: '/home', element: <HomePage />, handle: { title: '首页' } },
          { path: '/goods', element: <GoodsListPage />, handle: { title: '商品列表' } },
          { path: '/goods/:id', element: <GoodsDetailPage />, handle: { title: '商品详情' } },
          {
            path: '/publish',
            element: (
              <RequireAuth>
                <GoodsPublishPage />
              </RequireAuth>
            ),
            handle: { title: '发布商品' }
          },
          { path: '/register', element: <RegisterPage />, handle: { title: '注册' } },
          { path: '/want', element: <WantSquarePage />, handle: { title: '求购广场' } },
          // ⚠️ 路由顺序敏感：`want/publish` 必须在 `want/:id` 之前
          {
            path: '/want/publish',
            element: (
              <RequireAuth>
                <WantPublishPage />
              </RequireAuth>
            ),
            handle: { title: '发布求购' }
          },
          { path: '/want/:id', element: <WantDetailPage />, handle: { title: '求购详情' } },
          { path: '/swap', element: <SwapSquarePage />, handle: { title: '交换广场' } },
          // ⚠️ 同上：`swap/publish` 必须在 `swap/:id` 之前
          {
            path: '/swap/publish',
            element: (
              <RequireAuth>
                <SwapPublishPage />
              </RequireAuth>
            ),
            handle: { title: '发布交换' }
          },
          { path: '/swap/:id', element: <SwapDetailPage />, handle: { title: '交换详情' } },
          {
            path: '/orders',
            element: (
              <RequireAuth>
                <OrderListPage />
              </RequireAuth>
            ),
            handle: { title: '我的订单' }
          },
          {
            path: '/orders/:id',
            element: (
              <RequireAuth>
                <OrderDetailPage />
              </RequireAuth>
            ),
            handle: { title: '订单详情' }
          },
          {
            path: '/verify',
            element: (
              <RequireAuth>
                <VerifyPage />
              </RequireAuth>
            ),
            handle: { title: '校园认证' }
          },
          {
            path: '/message',
            element: (
              <RequireAuth>
                <MessageCenterPage />
              </RequireAuth>
            ),
            handle: { title: '消息中心' }
          },
          {
            path: '/profile',
            element: (
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            ),
            handle: { title: '个人中心' }
          }
        ]
      },
      { path: '*', element: <Navigate to="/home" replace /> }
    ]
  }
])

export default router
