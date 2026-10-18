import { baseApi } from './baseApi'
import type { Category, PageResult, SensitiveWord, Tag } from './types'

/**
 * 分类 / 标签 / 敏感词库。
 * 权限矩阵：分类标签 = SUPER+OPERATOR；敏感词库 = SUPER+AUDITOR。
 * 敏感词接口对新旧两种形态（PageResult 或平铺数组）都做类型兼容。
 */
export const catalogApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    // ---------- 分类 ----------
    getCategories: build.query<Category[], void>({
      query: () => ({ url: '/categories' }),
      providesTags: ['Category']
    }),
    createCategory: build.mutation<number, { parentId: number; name: string; sort: number }>({
      query: (body) => ({ url: '/categories', method: 'POST', data: body }),
      invalidatesTags: ['Category']
    }),
    updateCategory: build.mutation<
      void,
      { id: number; name: string; icon: string | null; sort: number; status: number }
    >({
      query: ({ id, ...body }) => ({ url: `/categories/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Category']
    }),
    deleteCategory: build.mutation<void, number>({
      query: (id) => ({ url: `/categories/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Category']
    }),

    // ---------- 标签 ----------
    getTags: build.query<Tag[], void>({
      query: () => ({ url: '/tags' }),
      providesTags: ['Tag']
    }),
    createTag: build.mutation<number, { name: string; sort: number }>({
      query: (body) => ({ url: '/tags', method: 'POST', data: body }),
      invalidatesTags: ['Tag']
    }),
    updateTag: build.mutation<void, { id: number; name: string; sort: number; status: number }>({
      query: ({ id, ...body }) => ({ url: `/tags/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Tag']
    }),
    deleteTag: build.mutation<void, number>({
      query: (id) => ({ url: `/tags/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Tag']
    }),

    // ---------- 敏感词库 ----------
    getSensitiveWords: build.query<
      PageResult<SensitiveWord> | SensitiveWord[],
      { keyword?: string; pageNum: number; pageSize: number }
    >({
      query: (params) => ({ url: '/sensitive-words', params }),
      providesTags: ['Word']
    }),
    addSensitiveWord: build.mutation<number, string>({
      query: (word) => ({ url: '/sensitive-words', method: 'POST', data: { word } }),
      invalidatesTags: ['Word']
    }),
    deleteSensitiveWord: build.mutation<void, number>({
      query: (id) => ({ url: `/sensitive-words/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Word']
    }),
    importSensitiveWords: build.mutation<number, string[]>({
      query: (words) => ({ url: '/sensitive-words/import', method: 'POST', data: { words } }),
      invalidatesTags: ['Word']
    })
  })
})

export const {
  useGetCategoriesQuery,
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
  useDeleteCategoryMutation,
  useGetTagsQuery,
  useCreateTagMutation,
  useUpdateTagMutation,
  useDeleteTagMutation,
  useGetSensitiveWordsQuery,
  useAddSensitiveWordMutation,
  useDeleteSensitiveWordMutation,
  useImportSensitiveWordsMutation
} = catalogApi
