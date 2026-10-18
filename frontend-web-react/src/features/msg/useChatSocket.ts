import { useEffect, useRef } from 'react'
import { toast } from 'sonner'
import { getToken } from '@/lib/auth-storage'
import type { MessageVO } from '@/api/types'

/** 服务端推送帧（与后端 `ChatWebSocketHandler`/`ChatServiceImpl` 协议一致） */
type WsPayload =
  | { type: 'CHAT_NEW'; message: MessageVO }
  | { type: 'CHAT_READ'; conversationId: number; readerId: number }
  | { type: 'ERROR'; code: number; message: string }
  | { type: string; [k: string]: unknown }

interface Options {
  /** 是否建立连接（未登录不连） */
  enabled: boolean
  onNewMessage: (message: MessageVO) => void
  onReadReceipt: (payload: { conversationId: number; readerId: number }) => void
}

const WS_PATH = '/ws'
const RECONNECT_MIN = 1000
const RECONNECT_MAX = 15000

/**
 * 站内私信 WebSocket（PRD CHT-02/05）。
 *
 * 与 Vue 版 `MessageCenterView` 的实现逐条对齐：
 * - 地址：`VITE_WS_URL` 优先，否则同源 `ws(s)://<host>/ws`；**token 走 query 参数**
 * - 重连：指数退避 1000ms → ×2 → 上限 15000ms；`onopen` 重置为 1000ms；手动关闭后不再重连
 * - 收帧：`CHAT_NEW` / `CHAT_READ` / `ERROR`（其余静默忽略；JSON 解析失败也静默）
 * - **不发业务帧**：发送消息仍走 REST（与旧版一致）
 *
 * ⚠️ **刻意修正（契约缺陷 #4）**：旧版直读 `localStorage.getItem('campus_market_token')`，
 * 导致 `remember=false`（token 落 sessionStorage）的用户永远连不上；这里改用 `getToken()`
 * （localStorage 优先、回退 sessionStorage），行为更正确且不影响原有场景。
 */
export function useChatSocket({ enabled, onNewMessage, onReadReceipt }: Options) {
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectDelay = useRef(RECONNECT_MIN)
  const reconnectTimer = useRef<number | null>(null)
  const manualClosed = useRef(false)

  // 回调放 ref，避免每次渲染都重连
  const newMsgRef = useRef(onNewMessage)
  const readRef = useRef(onReadReceipt)
  useEffect(() => {
    newMsgRef.current = onNewMessage
    readRef.current = onReadReceipt
  }, [onNewMessage, onReadReceipt])

  useEffect(() => {
    if (!enabled) return

    // ⚠️ 必须在建立连接前复位：React 18/19 的 StrictMode 会「mount → cleanup → mount」，
    // 上一次 cleanup 把 manualClosed 置为 true 后，若不复位，第二次 connect() 会直接 return，
    // 结果就是 socket 永远建不起来（表现为"实时消息收不到、已读回执不来"）。
    manualClosed.current = false

    const resolveUrl = () => {
      const override = import.meta.env.VITE_WS_URL
      if (override) return override
      const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
      return `${protocol}://${location.host}${WS_PATH}`
    }

    const handlePayload = (raw: string) => {
      let payload: WsPayload
      try {
        payload = JSON.parse(raw) as WsPayload
      } catch {
        return
      }
      if (payload.type === 'CHAT_NEW') {
        newMsgRef.current((payload as { message: MessageVO }).message)
      } else if (payload.type === 'CHAT_READ') {
        const p = payload as { conversationId: number; readerId: number }
        readRef.current({ conversationId: p.conversationId, readerId: p.readerId })
      } else if (payload.type === 'ERROR') {
        toast.error((payload as { message?: string }).message || '消息发送失败')
      }
    }

    const scheduleReconnect = () => {
      if (manualClosed.current) return
      if (reconnectTimer.current !== null) window.clearTimeout(reconnectTimer.current)
      reconnectTimer.current = window.setTimeout(() => {
        reconnectDelay.current = Math.min(reconnectDelay.current * 2, RECONNECT_MAX)
        connect()
      }, reconnectDelay.current)
    }

    const disconnect = (manual: boolean) => {
      manualClosed.current = manual
      if (reconnectTimer.current !== null) {
        window.clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      const ws = wsRef.current
      if (ws) {
        ws.onclose = null
        ws.onerror = null
        ws.onmessage = null
        try {
          ws.close()
        } catch {
          /* 连接已断开，忽略 */
        }
        wsRef.current = null
      }
    }

    function connect() {
      const token = getToken()
      if (!token || manualClosed.current) return
      disconnect(false)
      try {
        wsRef.current = new WebSocket(`${resolveUrl()}?token=${encodeURIComponent(token)}`)
      } catch {
        scheduleReconnect()
        return
      }
      wsRef.current.onopen = () => {
        reconnectDelay.current = RECONNECT_MIN
      }
      wsRef.current.onmessage = (event) => handlePayload(String(event.data))
      wsRef.current.onclose = scheduleReconnect
      wsRef.current.onerror = () => wsRef.current?.close()
    }

    connect()

    return () => disconnect(true)
    // enabled 变化（登录/登出）时重连；回调通过 ref 取最新值
  }, [enabled])
}
