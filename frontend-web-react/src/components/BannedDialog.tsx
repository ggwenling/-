import { useEffect, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

/**
 * 封禁提示弹窗（40301）。
 *
 * 与 Vue 版 frontend-web/src/api/request.js 的 `showBannedDialog` 行为等价：
 * - **去重**：弹窗未关闭期间忽略后续 40301（连续请求不刷屏）
 * - **不登出**：封禁用户仍可登录查看封禁原因与期限（PRD §3.1）
 * - 单按钮「我知道了」，无右上角关闭按钮（`showCloseButton={false}`）
 *
 * 用法：非组件环境调用 `showBannedDialog(message)`；在应用根部渲染一次 `<BannedDialogHost />`。
 */
let openFlag = false
let listener: ((message: string) => void) | null = null

export function showBannedDialog(message?: string): void {
  if (openFlag) return
  openFlag = true
  if (listener) {
    listener(message || '账号已被封禁')
  } else {
    // 宿主尚未挂载（极端情况）：放行标志，避免永久卡死
    openFlag = false
  }
}

export function BannedDialogHost() {
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    listener = (msg: string) => setMessage(msg)
    return () => {
      listener = null
    }
  }, [])

  const close = () => {
    setMessage(null)
    openFlag = false
  }

  return (
    <Dialog open={message !== null} onOpenChange={(next) => !next && close()}>
      <DialogContent showCloseButton={false} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>账号状态提醒</DialogTitle>
          <DialogDescription className="whitespace-pre-wrap text-foreground">{message || ''}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={close}>我知道了</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
