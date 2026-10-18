import { baseApi } from './baseApi'
import type { GoodsCardVO } from './types'

/**
 * 推荐位接口（契约见 docs/contracts-frontend-web.md §1.8）。
 *
 * - `recommendHome`：首页「猜你喜欢」，游客/新用户由后端热门兜底（REC-01/02）
 * - `recommendHot`：热度榜 Top10（REC-02）
 * - `recommendSimilar`：**Vue 版零调用**（详情页用 `GoodsDetailVO.similarGoods` 内联字段）。
 *   这里保留端点定义以便对齐契约，但页面不使用（与旧版行为一致）。
 */
export const recommendApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    recommendHome: build.query<GoodsCardVO[], number | void>({
      query: (limit = 8) => ({ url: '/recommend/home', params: { limit } })
    }),
    recommendHot: build.query<GoodsCardVO[], number | void>({
      query: (limit = 10) => ({ url: '/recommend/hot', params: { limit } })
    }),
    recommendSimilar: build.query<GoodsCardVO[], { goodsId: number; limit?: number }>({
      query: ({ goodsId, limit = 6 }) => ({ url: `/recommend/similar/${goodsId}`, params: { limit } })
    })
  })
})

export const { useRecommendHomeQuery, useRecommendHotQuery, useRecommendSimilarQuery } = recommendApi
