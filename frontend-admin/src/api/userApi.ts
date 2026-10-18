import { baseApi } from './baseApi'
import type { AdminUserVO, CreditLog, PageResult } from './types'

/** 用户管理（PRD ADM-05，SUPER/AUDITOR；信用分调整仅 SUPER） */
export const userApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageUsers: build.query<PageResult<AdminUserVO>, { username?: string; pageNum: number; pageSize: number }>({
      query: (params) => ({ url: '/users', params }),
      providesTags: ['User']
    }),
    getUserDetail: build.query<AdminUserVO, number>({
      query: (id) => ({ url: `/users/${id}` }),
      providesTags: ['User']
    }),
    pageUserCredits: build.query<PageResult<CreditLog>, { id: number; pageNum: number; pageSize: number }>({
      query: ({ id, ...params }) => ({ url: `/users/${id}/credits`, params }),
      providesTags: ['UserCredit']
    }),
    banUser: build.mutation<void, { id: number; reason: string; durationDays: number | null }>({
      query: ({ id, ...body }) => ({ url: `/users/${id}/ban`, method: 'POST', data: body }),
      invalidatesTags: ['User']
    }),
    unbanUser: build.mutation<void, number>({
      query: (id) => ({ url: `/users/${id}/unban`, method: 'POST' }),
      invalidatesTags: ['User']
    }),
    adjustCredit: build.mutation<void, { id: number; change: number; remark: string }>({
      query: ({ id, ...body }) => ({ url: `/users/${id}/credit`, method: 'POST', data: body }),
      invalidatesTags: ['User', 'UserCredit']
    })
  })
})

export const {
  usePageUsersQuery,
  useGetUserDetailQuery,
  usePageUserCreditsQuery,
  useBanUserMutation,
  useUnbanUserMutation,
  useAdjustCreditMutation
} = userApi
