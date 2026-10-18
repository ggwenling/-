import { configureStore } from '@reduxjs/toolkit'
import { setupListeners } from '@reduxjs/toolkit/query'
import { baseApi } from '@/api/baseApi'
import authReducer from '@/features/auth/authSlice'
import msgReducer from '@/features/msg/msgSlice'

/**
 * 全局 store：只放**跨页面共享**的状态。
 * - `auth`：token + 登录态用户摘要（多接口合成的对象）
 * - `msg`：未读私信 / 未读通知双红点
 * 其余服务端数据一律走 RTK Query 缓存，不在这里重复。
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    msg: msgReducer,
    [baseApi.reducerPath]: baseApi.reducer
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(baseApi.middleware)
})

setupListeners(store.dispatch)

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
