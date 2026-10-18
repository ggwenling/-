import { baseApi } from './baseApi'
import type { AdminRole, AdminVO, PageResult } from './types'

interface AccountPageArgs {
  adminId?: number
  pageNum: number
  pageSize: number
}

/** 管理员账号管理（PRD ADM-01，SUPER 专属） */
export const accountApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageAccounts: build.query<PageResult<AdminVO>, AccountPageArgs>({
      query: (params) => ({ url: '/accounts', params }),
      providesTags: ['Account']
    }),
    createAccount: build.mutation<number, { username: string; password: string; realName: string; role: AdminRole }>({
      query: (body) => ({ url: '/accounts', method: 'POST', data: body }),
      invalidatesTags: ['Account']
    }),
    /** 编辑：{realName, role, status}；启停也走本接口（无独立端点） */
    updateAccount: build.mutation<void, { id: number; realName: string; role: AdminRole; status: number }>({
      query: ({ id, ...body }) => ({ url: `/accounts/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Account']
    }),
    resetAccountPassword: build.mutation<void, { id: number; password: string }>({
      query: ({ id, password }) => ({ url: `/accounts/${id}/reset-password`, method: 'POST', data: { password } }),
      invalidatesTags: ['Account']
    })
  })
})

export const {
  usePageAccountsQuery,
  useCreateAccountMutation,
  useUpdateAccountMutation,
  useResetAccountPasswordMutation
} = accountApi
