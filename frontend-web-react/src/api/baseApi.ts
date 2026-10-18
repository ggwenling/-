import { createApi } from '@reduxjs/toolkit/query/react'
import { axiosBaseQuery } from './baseQuery'

/**
 * RTK Query 根 API：所有资源分片通过 `injectEndpoints` 挂载（单一 reducer/middleware）。
 * 标签用于写操作后的缓存失效，保持"操作后列表自动刷新"的旧语义。
 */
export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: axiosBaseQuery,
  tagTypes: [
    'Me',
    'Category',
    'Tag',
    'Goods',
    'GoodsMine',
    'Favorite',
    'WantPost',
    'WantOffer',
    'SwapPost',
    'SwapRequest',
    'Order',
    'Review',
    'Credit',
    'Conversation',
    'Message',
    'Notification',
    'Report'
  ],
  endpoints: () => ({})
})

export default baseApi
