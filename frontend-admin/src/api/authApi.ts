import { baseApi } from './baseApi'
import type { AdminLoginVO, AdminVO } from './types'

/** 后台认证（PRD ADM-01 / T12）：登录、当前管理员、强制改密、登出 */
export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<AdminLoginVO, { username: string; password: string }>({
      query: (body) => ({ url: '/auth/login', method: 'POST', data: body })
    }),
    getMe: build.query<AdminVO, void>({
      query: () => ({ url: '/auth/me' }),
      providesTags: ['Me']
    }),
    changePassword: build.mutation<void, { oldPassword: string; newPassword: string }>({
      query: (body) => ({ url: '/auth/change-password', method: 'POST', data: body }),
      invalidatesTags: ['Me']
    }),
    logout: build.mutation<void, void>({
      query: () => ({ url: '/auth/logout', method: 'POST' })
    })
  })
})

export const {
  useLoginMutation,
  useGetMeQuery,
  useLazyGetMeQuery,
  useChangePasswordMutation,
  useLogoutMutation
} = authApi
