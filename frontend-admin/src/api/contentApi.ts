import { baseApi } from './baseApi'
import type { AdminContentVO, PageResult } from './types'

/** 内容巡查处置（PRD ADM-02，SUPER/AUDITOR）：GOODS/WANT/SWAP 统一投影 */
export const contentApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageContents: build.query<
      PageResult<AdminContentVO>,
      { targetType: string; status?: string; keyword?: string; pageNum: number; pageSize: number }
    >({
      query: (params) => ({ url: '/contents', params }),
      providesTags: ['Content']
    }),
    takeDownContent: build.mutation<void, { targetType: string; id: number; reason: string }>({
      query: ({ targetType, id, reason }) => ({
        url: `/contents/${targetType}/${id}/take-down`,
        method: 'POST',
        data: { reason }
      }),
      invalidatesTags: ['Content']
    }),
    restoreContent: build.mutation<void, { targetType: string; id: number }>({
      query: ({ targetType, id }) => ({ url: `/contents/${targetType}/${id}/restore`, method: 'POST' }),
      invalidatesTags: ['Content']
    }),
    deleteContent: build.mutation<void, { targetType: string; id: number; reason: string }>({
      query: ({ targetType, id, reason }) => ({
        url: `/contents/${targetType}/${id}/delete`,
        method: 'POST',
        data: { reason }
      }),
      invalidatesTags: ['Content']
    })
  })
})

export const {
  usePageContentsQuery,
  useTakeDownContentMutation,
  useRestoreContentMutation,
  useDeleteContentMutation
} = contentApi
