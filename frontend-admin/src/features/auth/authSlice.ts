import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { AdminInfo, AdminLoginVO, AdminVO } from '@/api/types'
import type { RootState } from '@/app/store'

// token 存储键与旧版 frontend-admin 保持一致，便于两版平滑替换
const TOKEN_KEY = 'campus_market_admin_token'

export const getToken = (): string => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (token: string): void => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = (): void => localStorage.removeItem(TOKEN_KEY)

interface AuthState {
  token: string
  /** 内存态管理员信息（刷新后由 /auth/me 恢复） */
  admin: AdminInfo | null
}

const initialState: AuthState = {
  token: getToken(),
  admin: null
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    /** 登录成功：写入 token 与管理员信息（AdminLoginVO 扁平结构） */
    setCredentials(state, action: PayloadAction<AdminLoginVO>) {
      const d = action.payload
      state.token = d.token
      setToken(d.token)
      state.admin = {
        id: d.adminId,
        username: d.username,
        realName: d.realName,
        role: d.role,
        mustChangePassword: d.mustChangePassword === true
      }
    },
    /** 刷新/改密后从 /auth/me 同步（AdminVO） */
    setAdmin(state, action: PayloadAction<AdminVO | AdminInfo>) {
      const d = action.payload as AdminVO
      state.admin = {
        id: d.id,
        username: d.username,
        realName: d.realName,
        role: d.role,
        mustChangePassword: (d as AdminVO).mustChangePassword === 1 || (d as AdminInfo).mustChangePassword === true
      }
    },
    /** 登出 / 登录态失效 */
    clearCredentials(state) {
      state.token = ''
      state.admin = null
      clearToken()
    }
  }
})

export const { setCredentials, setAdmin, clearCredentials } = authSlice.actions

// 直接针对根状态的选择器（类型安全，避免切片/根状态歧义）
export const selectToken = (state: RootState) => state.auth.token
export const selectAdmin = (state: RootState) => state.auth.admin
export const selectRole = (state: RootState) => state.auth.admin?.role || ''
export const selectIsLoggedIn = (state: RootState) => !!state.auth.token

export default authSlice.reducer
