import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ALL_ROLES } from '@/lib/constants'
import { AdminLayout } from '@/layouts/AdminLayout'
import { PageLoader } from '@/components/PageLoader'
import { RequireAuth, RequireRole } from './guards'

// 路由级代码分割：各页面按需加载，降低首屏体积
const LoginPage = lazy(() => import('@/pages/login/LoginPage'))
const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage'))
const ContentManagePage = lazy(() => import('@/pages/content/ContentManagePage'))
const ReportWorkbenchPage = lazy(() => import('@/pages/report/ReportWorkbenchPage'))
const UserManagePage = lazy(() => import('@/pages/user/UserManagePage'))
const CategoryManagePage = lazy(() => import('@/pages/category/CategoryManagePage'))
const OperationConfigPage = lazy(() => import('@/pages/operation/OperationConfigPage'))
const SensitiveWordPage = lazy(() => import('@/pages/sensitive-word/SensitiveWordPage'))
const OperationLogPage = lazy(() => import('@/pages/log/OperationLogPage'))
const AdminManagePage = lazy(() => import('@/pages/admin/AdminManagePage'))

/**
 * 路由表对齐 PRD §7.2 后台 10 个页面；roles 摘自 §4.2 RBAC 权限矩阵。
 * 页面级 Suspense 落在 AdminLayout 的 Outlet 外层（登录页单独包一层）。
 */
export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspense fallback={<PageLoader />}>
        <LoginPage />
      </Suspense>
    )
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      {
        path: 'dashboard',
        element: (
          <RequireRole roles={ALL_ROLES}>
            <DashboardPage />
          </RequireRole>
        )
      },
      {
        path: 'content',
        element: (
          <RequireRole roles={['super', 'auditor']}>
            <ContentManagePage />
          </RequireRole>
        )
      },
      {
        path: 'report',
        element: (
          <RequireRole roles={['super', 'auditor']}>
            <ReportWorkbenchPage />
          </RequireRole>
        )
      },
      {
        path: 'user',
        element: (
          <RequireRole roles={['super', 'auditor']}>
            <UserManagePage />
          </RequireRole>
        )
      },
      {
        path: 'category',
        element: (
          <RequireRole roles={['super', 'operator']}>
            <CategoryManagePage />
          </RequireRole>
        )
      },
      {
        path: 'operation',
        element: (
          <RequireRole roles={['super', 'operator']}>
            <OperationConfigPage />
          </RequireRole>
        )
      },
      {
        path: 'sensitive-word',
        element: (
          <RequireRole roles={['super', 'auditor']}>
            <SensitiveWordPage />
          </RequireRole>
        )
      },
      {
        path: 'log',
        element: (
          <RequireRole roles={ALL_ROLES}>
            <OperationLogPage />
          </RequireRole>
        )
      },
      {
        path: 'admin',
        element: (
          <RequireRole roles={['super']}>
            <AdminManagePage />
          </RequireRole>
        )
      }
    ]
  },
  { path: '*', element: <Navigate to="/dashboard" replace /> }
])
