import { baseApi } from './baseApi'
import type { PageResult, ReportCreateDTO, ReportVO } from './types'

/**
 * 举报接口（契约见 docs/contracts-frontend-web.md §1.7）。
 * - `POST /reports`：同目标重复提交待处理工单会被后端拒绝；不能举报自己发布的内容
 * - `GET /reports/my`：个人中心「我的举报」Tab，`status` 可省（0/1/2）
 */
export const reportApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    submitReport: build.mutation<void, ReportCreateDTO>({
      query: (body) => ({ url: '/reports', method: 'POST', data: body }),
      invalidatesTags: ['Report']
    }),
    pageMyReports: build.query<PageResult<ReportVO>, { status?: number; pageNum: number; pageSize: number }>({
      query: ({ status, pageNum, pageSize }) => ({
        url: '/reports/my',
        params: { ...(status === undefined ? {} : { status }), pageNum, pageSize }
      }),
      providesTags: ['Report']
    })
  })
})

export const { useSubmitReportMutation, usePageMyReportsQuery, useLazyPageMyReportsQuery } = reportApi
