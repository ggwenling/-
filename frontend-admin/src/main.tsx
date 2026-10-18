import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'
import { store } from '@/app/store'
import { router } from '@/router/routes'
import { ConfirmProvider } from '@/components/confirm'
import { PromptProvider } from '@/components/prompt'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import '@/styles/index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <TooltipProvider>
        <ConfirmProvider>
          <PromptProvider>
            <RouterProvider router={router} />
            <Toaster />
          </PromptProvider>
        </ConfirmProvider>
      </TooltipProvider>
    </Provider>
  </StrictMode>
)
