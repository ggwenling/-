import { baseApi } from './baseApi'
import type { PageResult, ReportVO } from './types'

/** 举报工单处置（PRD ADM-04，SUPER/AUDITOR） */
export const reportApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageReports: build.query<
      PageResult<ReportVO>,
      { status?: number | ''; targetType?: string; pageNum: number; pageSize: number }
    >({
      query: (params) => ({ url: '/reports', params }),
      providesTags: ['Report']
    }),
    getReportDetail: build.query<ReportVO, number>({
      query: (id) => ({ url: `/reports/${id}` }),
      providesTags: ['Report']
    }),
    handleReport: build.mutation<void, { id: number; actions: string[]; result: string }>({
      query: ({ id, ...body }) => ({ url: `/reports/${id}/handle`, method: 'POST', data: body }),
      invalidatesTags: ['Report', 'Content', 'User']
    }),
    rejectReport: build.mutation<void, { id: number; result: string }>({
      query: ({ id, result }) => ({ url: `/reports/${id}/reject`, method: 'POST', data: { result } }),
      invalidatesTags: ['Report']
    })
  })
})

export const {
  usePageReportsQuery,
  useGetReportDetailQuery,
  useHandleReportMutation,
  useRejectReportMutation
} = reportApi
