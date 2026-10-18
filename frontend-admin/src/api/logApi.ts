import { baseApi } from './baseApi'
import type { OperationLog, PageResult } from './types'

/** 操作日志（PRD ADM-09，三角色只读）；后端筛选仅支持 adminId/action */
export const logApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageOperationLogs: build.query<
      PageResult<OperationLog>,
      { adminId?: number; action?: string; pageNum: number; pageSize: number }
    >({
      query: (params) => ({ url: '/operation-logs', params }),
      providesTags: ['Log']
    })
  })
})

export const { usePageOperationLogsQuery } = logApi
