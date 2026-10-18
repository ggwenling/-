import { baseApi } from './baseApi'
import type {
  ActionReasonDTO,
  OfferCreateDTO,
  PageResult,
  WantPostCardVO,
  WantPostDetailVO,
  WantPostListQuery,
  WantPostPublishDTO
} from './types'

/**
 * 求购接口（契约见 docs/contracts-frontend-web.md §1.5）。
 *
 * 注意：
 * - 列表默认只返回 `OPEN`（`DELETED` 永不返回）；`sort` 支持 `latest` / `budgetDesc`
 * - 详情里 `offers` **仅帖主可见**（非帖主为 `null`，不是空数组）；`myOffer` 为当前用户自己的应约
 * - 应约/接受/拒绝的路径在 `offers` 段下（注意与 swap 的 `requests` 段不同）
 */
export const wantApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    pageWantPosts: build.query<PageResult<WantPostCardVO>, WantPostListQuery>({
      query: (params) => ({ url: '/want-posts', params }),
      providesTags: ['WantPost']
    }),
    getWantPost: build.query<WantPostDetailVO, number>({
      query: (id) => ({ url: `/want-posts/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'WantPost', id }]
    }),
    /** 发布求购 → 返回新 id */
    publishWantPost: build.mutation<number, WantPostPublishDTO>({
      query: (body) => ({ url: '/want-posts', method: 'POST', data: body }),
      invalidatesTags: ['WantPost']
    }),
    updateWantPost: build.mutation<void, { id: number; body: WantPostPublishDTO }>({
      query: ({ id, body }) => ({ url: `/want-posts/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['WantPost']
    }),
    /** 关闭求购帖（理由可选） */
    closeWantPost: build.mutation<void, { id: number; reason?: string }>({
      query: ({ id, reason }) => ({
        url: `/want-posts/${id}/close`,
        method: 'POST',
        data: { reason } satisfies ActionReasonDTO
      }),
      invalidatesTags: ['WantPost']
    }),
    /** 删除求购帖（软删，30 天内可恢复） */
    removeWantPost: build.mutation<void, number>({
      query: (id) => ({ url: `/want-posts/${id}`, method: 'DELETE' }),
      invalidatesTags: ['WantPost']
    }),
    /** 应约（报价必填 + 留言可选） */
    createOffer: build.mutation<void, { postId: number; body: OfferCreateDTO }>({
      query: ({ postId, body }) => ({ url: `/want-posts/${postId}/offers`, method: 'POST', data: body }),
      invalidatesTags: ['WantPost', 'WantOffer']
    }),
    /** 撤回应约 */
    withdrawOffer: build.mutation<void, number>({
      query: (offerId) => ({ url: `/want-posts/offers/${offerId}/withdraw`, method: 'POST' }),
      invalidatesTags: ['WantPost', 'WantOffer']
    }),
    /** 接受应约（原子：帖 DEALT + 其余应约自动拒绝 + 生成 PURCHASE 订单） */
    acceptOffer: build.mutation<void, number>({
      query: (offerId) => ({ url: `/want-posts/offers/${offerId}/accept`, method: 'POST' }),
      invalidatesTags: ['WantPost', 'WantOffer', 'Order']
    }),
    /** 拒绝应约（理由可选） */
    rejectOffer: build.mutation<void, { offerId: number; reason?: string }>({
      query: ({ offerId, reason }) => ({
        url: `/want-posts/offers/${offerId}/reject`,
        method: 'POST',
        data: { reason } satisfies ActionReasonDTO
      }),
      invalidatesTags: ['WantPost', 'WantOffer']
    })
  })
})

export const {
  usePageWantPostsQuery,
  useGetWantPostQuery,
  usePublishWantPostMutation,
  useUpdateWantPostMutation,
  useCloseWantPostMutation,
  useRemoveWantPostMutation,
  useCreateOfferMutation,
  useWithdrawOfferMutation,
  useAcceptOfferMutation,
  useRejectOfferMutation
} = wantApi
