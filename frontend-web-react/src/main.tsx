import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'
import { store } from '@/app/store'
import { router } from '@/router/routes'
import { setUnauthorizedHandler } from '@/api/baseQuery'
import { BannedDialogHost } from '@/components/BannedDialog'
import { ConfirmProvider } from '@/components/confirm'
import { PromptProvider } from '@/components/prompt'
import { Toaster } from '@/components/ui/sonner'
import '@/styles/index.css'

/**
 * 登录态失效（40100/40101）时的导航：跳登录页并带上 redirect（与 Vue 版 request.js 一致）。
 * 用注入回调而不是在 baseQuery 里 import router，避免 router ↔ store ↔ api 循环依赖。
 */
setUnauthorizedHandler(() => {
  const { pathname, search } = router.state.location
  const redirect = encodeURIComponent(pathname + search)
  void router.navigate(`/login?redirect=${redirect}`)
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      {/* 命令式确认框 / 文本输入弹窗（替代 ElMessageBox.confirm / prompt） */}
      <ConfirmProvider>
        <PromptProvider>
          <RouterProvider router={router} />
          <Toaster />
          <BannedDialogHost />
        </PromptProvider>
      </ConfirmProvider>
    </Provider>
  </StrictMode>
)
