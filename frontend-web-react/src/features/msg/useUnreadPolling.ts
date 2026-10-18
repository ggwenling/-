import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectBanned, selectIsLoggedIn } from '@/features/auth/authSlice'
import { resetUnread, setUnread } from './msgSlice'
import { useGetChatUnreadQuery, useGetNotificationUnreadCountQuery } from '@/api/messageApi'
import { POLL_INTERVAL_MS } from '@/lib/constants'

/**
 * 双红点轮询（PRD CHT-05 / NTF-02），与 Vue 版 `stores/msg.js` 的 `startPolling` 语义逐条对齐：
 * 1. 未登录 → 清零且不请求
 * 2. 请求失败静默（RTK Query 轮询下一轮重试，不弹错）
 * 3. 登录/解封后**立即拉一次**，之后每 30s 一次（`pollingInterval`）
 * 4. **封禁账号不轮询** —— 其业务接口一律 40301，轮询只会反复弹窗
 *
 * 说明：Vue 版在"打开会话/发送消息/标记已读"后手动 `msgStore.refresh()`；
 * 这里由 RTK Query 的 tag 失效自动触发重取（`markConversationRead` / `sendMessage` 失效 `Conversation`）。
 */
export function useUnreadPolling(): void {
  const dispatch = useAppDispatch()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const banned = useAppSelector(selectBanned)
  const active = isLoggedIn && !banned

  const { data: chatUnread } = useGetChatUnreadQuery(undefined, {
    skip: !active,
    pollingInterval: POLL_INTERVAL_MS
  })
  const { data: notifyUnread } = useGetNotificationUnreadCountQuery(undefined, {
    skip: !active,
    pollingInterval: POLL_INTERVAL_MS
  })

  useEffect(() => {
    if (!active) {
      dispatch(resetUnread())
      return
    }
    dispatch(setUnread({ chat: chatUnread?.messageCount, notify: notifyUnread }))
  }, [active, chatUnread, notifyUnread, dispatch])
}
