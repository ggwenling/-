import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'

/**
 * 导航双红点（未读私信 / 未读通知）。
 *
 * 与 Vue 版 `stores/msg.js` 等价：纯内存状态、无持久化；数据由
 * `useUnreadPolling()`（30s 轮询 + 登录后立即拉一次）写入。
 */
interface MsgState {
  unreadChat: number
  unreadNotify: number
}

const initialState: MsgState = {
  unreadChat: 0,
  unreadNotify: 0
}

const msgSlice = createSlice({
  name: 'msg',
  initialState,
  reducers: {
    setUnread(state, action: PayloadAction<{ chat?: number; notify?: number }>) {
      const { chat, notify } = action.payload
      if (typeof chat === 'number' && Number.isFinite(chat)) state.unreadChat = chat
      if (typeof notify === 'number' && Number.isFinite(notify)) state.unreadNotify = notify
    },
    /** 未登录 / 登出时清零（Vue 版 refresh() 的未登录分支） */
    resetUnread(state) {
      state.unreadChat = 0
      state.unreadNotify = 0
    }
  }
})

export const { setUnread, resetUnread } = msgSlice.actions

export const selectUnreadChat = (state: RootState) => state.msg.unreadChat
export const selectUnreadNotify = (state: RootState) => state.msg.unreadNotify

export default msgSlice.reducer
