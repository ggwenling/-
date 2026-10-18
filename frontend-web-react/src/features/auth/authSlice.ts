import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { LoginVO, UserDetailVO, UserInfo } from '@/api/types'
import {
  clearRefreshToken,
  clearToken,
  getToken,
  setRefreshToken,
  setToken
} from '@/lib/auth-storage'
import type { RootState } from '@/app/store'

/**
 * 登录态切片。
 *
 * 与 Vue 版 `frontend-web/src/stores/user.js` 的口径逐条对齐：
 * - `token` 持久化：默认 localStorage；`remember === false` 落 sessionStorage（写入时清另一侧）
 * - `userInfo` 是**多接口写入的合成对象**（登录响应 + `/users/me`），因此放全局 store 而不是 RTK Query 缓存
 * - `userId` 双来源：登录响应是 `userId`，`/users/me`（UserDetailVO）主键是 `id` → 必须显式映射
 * - `banned` 双来源：登录响应 `banned`（boolean）与 `/users/me` 的 `status === 1`
 */
interface AuthState {
  token: string
  userInfo: UserInfo | null
}

const initialState: AuthState = {
  token: getToken(),
  userInfo: null
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    /** 登录/注册成功：写入 token（按 remember 选择存储）与用户摘要 */
    setCredentials(state, action: PayloadAction<{ login: LoginVO; remember?: boolean }>) {
      const { login: d, remember = true } = action.payload
      state.token = d.token
      setToken(d.token, remember)
      setRefreshToken(d.refreshToken || '', remember)
      state.userInfo = {
        userId: d.userId,
        nickname: d.nickname,
        avatar: d.avatar,
        authStatus: d.authStatus,
        banned: d.banned === true,
        banReason: d.banReason || '',
        bannedUntil: d.bannedUntil || ''
      }
    },
    /** 拉取/刷新资料（`GET /users/me`）；同时把封禁态与信用分补齐 */
    setProfile(state, action: PayloadAction<UserDetailVO>) {
      const d = action.payload
      state.userInfo = {
        ...(state.userInfo || {}),
        ...d,
        // UserDetailVO 主键是 id：isOwner 判定与互评 revieweeId 都依赖 userId
        userId: d.id ?? state.userInfo?.userId,
        banned: d.status === 1,
        banReason: d.banReason || '',
        bannedUntil: d.bannedUntil || ''
      }
    },
    /** 登出 / 登录态失效：清空两侧存储与内存态 */
    clearCredentials(state) {
      state.token = ''
      state.userInfo = null
      clearToken()
      clearRefreshToken()
    }
  }
})

export const { setCredentials, setProfile, clearCredentials } = authSlice.actions

export const selectToken = (state: RootState) => state.auth.token
export const selectUserInfo = (state: RootState) => state.auth.userInfo
export const selectIsLoggedIn = (state: RootState) => !!state.auth.token
export const selectIsVerified = (state: RootState) => state.auth.userInfo?.authStatus === 1
export const selectUserId = (state: RootState) => state.auth.userInfo?.userId
export const selectCreditScore = (state: RootState) => state.auth.userInfo?.creditScore
export const selectBanned = (state: RootState) => state.auth.userInfo?.banned === true

export default authSlice.reducer
