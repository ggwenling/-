import { baseApi } from './baseApi'
import type {
  CategoryTreeVO,
  FavoriteVO,
  GoodsCardVO,
  GoodsDetailVO,
  GoodsListQuery,
  GoodsPublishDTO,
  PageResult,
  TagVO
} from './types'

/**
 * 商品、分类、标签与收藏接口（契约见 docs/contracts-frontend-web.md §1.2）。
 *
 * 注意：
 * - `GET /goods/my` 会**静默忽略** `status/categoryId` 等多余参数（后端只认 pageNum/pageSize）
 * - `GET /goods` 的分页无 `@Max` 上限；`sort` 非法值后端回落 `latest`
 * - `POST /goods` 返回新商品 id；`PUT /goods/{id}` 返回 null
 * - 收藏：`POST /favorites` body `{goodsId}`、`DELETE /favorites/{goodsId}`
 */
export const goodsApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    /** 商品列表（筛选 + 排序 + 分页） */
    pageGoods: build.query<PageResult<GoodsCardVO>, GoodsListQuery>({
      query: (params) => ({ url: '/goods', params }),
      providesTags: ['Goods']
    }),
    /** 商品详情（含 similarGoods 与 favorited） */
    getGoods: build.query<GoodsDetailVO, number>({
      query: (id) => ({ url: `/goods/${id}` }),
      providesTags: (_r, _e, id) => [{ type: 'Goods', id }]
    }),
    /** 我的商品（仅 pageNum/pageSize 有效） */
    pageMyGoods: build.query<PageResult<GoodsCardVO>, { pageNum: number; pageSize: number }>({
      query: ({ pageNum, pageSize }) => ({ url: '/goods/my', params: { pageNum, pageSize } }),
      providesTags: ['GoodsMine']
    }),
    /** 发布商品 → 返回新 id */
    publishGoods: build.mutation<number, GoodsPublishDTO>({
      query: (body) => ({ url: '/goods', method: 'POST', data: body }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 编辑商品 */
    updateGoods: build.mutation<void, { id: number; body: GoodsPublishDTO }>({
      query: ({ id, body }) => ({ url: `/goods/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 删除商品（软删，30 天内可恢复） */
    deleteGoods: build.mutation<void, number>({
      query: (id) => ({ url: `/goods/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 下架 */
    offSaleGoods: build.mutation<void, number>({
      query: (id) => ({ url: `/goods/${id}/off-sale`, method: 'POST' }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 重新上架 */
    onSaleGoods: build.mutation<void, number>({
      query: (id) => ({ url: `/goods/${id}/on-sale`, method: 'POST' }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 恢复（DELETED → OFF_SALE，需再点上架） */
    restoreGoods: build.mutation<void, number>({
      query: (id) => ({ url: `/goods/${id}/restore`, method: 'POST' }),
      invalidatesTags: ['Goods', 'GoodsMine']
    }),
    /** 分类二级树（全站公共只读，RTK Query 天然去重缓存） */
    listCategories: build.query<CategoryTreeVO[], void>({
      query: () => ({ url: '/categories' }),
      providesTags: ['Category']
    }),
    /** 标签列表（发布表单多选 0~5） */
    listTags: build.query<TagVO[], void>({
      query: () => ({ url: '/tags' }),
      providesTags: ['Tag']
    }),
    /** 加入收藏 */
    addFavorite: build.mutation<void, number>({
      query: (goodsId) => ({ url: '/favorites', method: 'POST', data: { goodsId } }),
      invalidatesTags: ['Favorite']
    }),
    /** 取消收藏 */
    removeFavorite: build.mutation<void, number>({
      query: (goodsId) => ({ url: `/favorites/${goodsId}`, method: 'DELETE' }),
      invalidatesTags: ['Favorite']
    }),
    /** 我的收藏（注意 `goods` 可能为 null，渲染前需 filter） */
    pageMyFavorites: build.query<PageResult<FavoriteVO>, { pageNum: number; pageSize: number }>({
      query: ({ pageNum, pageSize }) => ({ url: '/favorites/my', params: { pageNum, pageSize } }),
      providesTags: ['Favorite']
    })
  })
})

export const {
  usePageGoodsQuery,
  useGetGoodsQuery,
  useLazyGetGoodsQuery,
  usePageMyGoodsQuery,
  useLazyPageMyGoodsQuery,
  usePublishGoodsMutation,
  useUpdateGoodsMutation,
  useDeleteGoodsMutation,
  useOffSaleGoodsMutation,
  useOnSaleGoodsMutation,
  useRestoreGoodsMutation,
  useListCategoriesQuery,
  useListTagsQuery,
  useAddFavoriteMutation,
  useRemoveFavoriteMutation,
  usePageMyFavoritesQuery,
  useLazyPageMyFavoritesQuery
} = goodsApi
