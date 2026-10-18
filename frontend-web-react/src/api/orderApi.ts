import { baseApi } from './baseApi'
import type {
  OrderActionDTO,
  OrderDetailVO,
  OrderListVO,
  OrderStatus,
  OrderViewRole,
  PageResult,
  ReviewCreateDTO,
  ReviewVO
} from './types'

/**
 * 订单与评价接口（契约见 docs/contracts-frontend-web.md §1.3）。
 *
 * 关键口径：
 * - `GET /orders` 的 **`role` 必填**（buyer/seller），缺失后端返回 `50000`
 * - 操作按钮由后端 `canConfirm/canReject/canCancel/canComplete/canReview` 驱动，**前端不重算状态机**
 * - `GET /reviews` 的 **`revieweeId` 必填**，缺失同样 50000（个人中心需先拿到 userId 再请求）
 */
export const orderApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    /**
     * 对在售商品下单（SALE）。
     * ⚠️ 后端返回的是 **整个 `OrderDetailVO`**（不是 id）——新订单 id 要取 `data.id`
     * （证据：`OrderController.create` → `Result<OrderDetailVO>`；Vue 版即 `res.data.id`）。
     */
    createOrder: build.mutation<OrderDetailVO, number>({
      query: (goodsId) => ({ url: '/orders', method: 'POST', data: { goodsId } }),
      invalidatesTags: ['Order', 'Goods']
    }),
    /** 订单列表（role 必填） */
    pageOrders: build.query<
      PageResult<OrderListVO>,
      { role: OrderViewRole; status?: OrderStatus; pageNum: number; pageSize: number }
    >({
      query: ({ role, status, pageNum, pageSize }) => ({
        url: '/orders',
        params: { role, ...(status ? { status } : {}), pageNum, pageSize }
      }),
      providesTags: ['Order']
    }),
    /** 订单详情 */
    getOrder: build.query<OrderDetailVO, number>({
      query: (id) => ({ url: `/orders/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'Order', id }]
    }),
    /** 卖家确认出售 */
    confirmOrder: build.mutation<void, number>({
      query: (id) => ({ url: `/orders/${id}/confirm`, method: 'POST' }),
      invalidatesTags: ['Order']
    }),
    /** 卖家拒绝（可带理由） */
    rejectOrder: build.mutation<void, { id: number; reason?: string }>({
      query: ({ id, reason }) => ({
        url: `/orders/${id}/reject`,
        method: 'POST',
        data: { reason } satisfies OrderActionDTO
      }),
      invalidatesTags: ['Order']
    }),
    /** 买家取消（列表页传空字符串，与旧版一致） */
    cancelOrder: build.mutation<void, { id: number; reason?: string }>({
      query: ({ id, reason }) => ({
        url: `/orders/${id}/cancel`,
        method: 'POST',
        data: { reason } satisfies OrderActionDTO
      }),
      invalidatesTags: ['Order']
    }),
    /** 确认完成（SWAP 为双方各确认一次） */
    completeOrder: build.mutation<void, number>({
      query: (id) => ({ url: `/orders/${id}/complete`, method: 'POST' }),
      invalidatesTags: ['Order', 'Goods']
    }),
    /** 提交评价（1~5 星 + 选填内容） */
    reviewOrder: build.mutation<void, { id: number; body: ReviewCreateDTO }>({
      query: ({ id, body }) => ({ url: `/orders/${id}/review`, method: 'POST', data: body }),
      invalidatesTags: ['Order', 'Review']
    }),
    /** 某人收到的评价（revieweeId 必填） */
    pageReviews: build.query<PageResult<ReviewVO>, { revieweeId: number; pageNum: number; pageSize: number }>({
      query: ({ revieweeId, pageNum, pageSize }) => ({
        url: '/reviews',
        params: { revieweeId, pageNum, pageSize }
      }),
      providesTags: ['Review']
    })
  })
})

export const {
  useCreateOrderMutation,
  usePageOrdersQuery,
  useGetOrderQuery,
  useConfirmOrderMutation,
  useRejectOrderMutation,
  useCancelOrderMutation,
  useCompleteOrderMutation,
  useReviewOrderMutation,
  usePageReviewsQuery,
  useLazyPageOrdersQuery,
  useLazyPageReviewsQuery
} = orderApi
