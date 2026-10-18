import { baseApi } from './baseApi'
import type {
  ActionReasonDTO,
  PageResult,
  SwapPostCardVO,
  SwapPostDetailVO,
  SwapPostListQuery,
  SwapPostPublishDTO,
  SwapRequestCreateDTO
} from './types'

/**
 * 交换接口（契约见 docs/contracts-frontend-web.md §1.6）。
 *
 * 注意：
 * - 列表默认只返回 `OPEN`；`allowDiff` 是数字 `0/1`（不是 boolean），`diffAmount` 仅在 `allowDiff=1` 时有值
 * - 详情里 `requests` **仅帖主可见**（非帖主为 `null`）；`myRequest` 为当前用户自己的请求
 * - 请求相关路径在 `requests` 段下（`/swap-posts/requests/{id}/accept|reject`）
 */
export const swapApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageSwapPosts: build.query<PageResult<SwapPostCardVO>, SwapPostListQuery>({
      query: (params) => ({ url: '/swap-posts', params }),
      providesTags: ['SwapPost']
    }),
    getSwapPost: build.query<SwapPostDetailVO, number>({
      query: (id) => ({ url: `/swap-posts/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'SwapPost', id }]
    }),
    /** 发布交换 → 返回新 id */
    publishSwapPost: build.mutation<number, SwapPostPublishDTO>({
      query: (body) => ({ url: '/swap-posts', method: 'POST', data: body }),
      invalidatesTags: ['SwapPost']
    }),
    updateSwapPost: build.mutation<void, { id: number; body: SwapPostPublishDTO }>({
      query: ({ id, body }) => ({ url: `/swap-posts/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['SwapPost']
    }),
    closeSwapPost: build.mutation<void, { id: number; reason?: string }>({
      query: ({ id, reason }) => ({
        url: `/swap-posts/${id}/close`,
        method: 'POST',
        data: { reason } satisfies ActionReasonDTO
      }),
      invalidatesTags: ['SwapPost']
    }),
    removeSwapPost: build.mutation<void, number>({
      query: (id) => ({ url: `/swap-posts/${id}`, method: 'DELETE' }),
      invalidatesTags: ['SwapPost']
    }),
    /** 发起交换（物品说明必填，可选关联自己在售商品） */
    createSwapRequest: build.mutation<void, { postId: number; body: SwapRequestCreateDTO }>({
      query: ({ postId, body }) => ({ url: `/swap-posts/${postId}/requests`, method: 'POST', data: body }),
      invalidatesTags: ['SwapPost', 'SwapRequest']
    }),
    /** 同意交换请求（原子：生成 SCHEDULED 的 SWAP 订单 + 其余请求关闭） */
    acceptSwapRequest: build.mutation<void, number>({
      query: (requestId) => ({ url: `/swap-posts/requests/${requestId}/accept`, method: 'POST' }),
      invalidatesTags: ['SwapPost', 'SwapRequest', 'Order']
    }),
    /** 拒绝交换请求（理由可选） */
    rejectSwapRequest: build.mutation<void, { requestId: number; reason?: string }>({
      query: ({ requestId, reason }) => ({
        url: `/swap-posts/requests/${requestId}/reject`,
        method: 'POST',
        data: { reason } satisfies ActionReasonDTO
      }),
      invalidatesTags: ['SwapPost', 'SwapRequest']
    })
  })
})

export const {
  usePageSwapPostsQuery,
  useGetSwapPostQuery,
  usePublishSwapPostMutation,
  useUpdateSwapPostMutation,
  useCloseSwapPostMutation,
  useRemoveSwapPostMutation,
  useCreateSwapRequestMutation,
  useAcceptSwapRequestMutation,
  useRejectSwapRequestMutation
} = swapApi
