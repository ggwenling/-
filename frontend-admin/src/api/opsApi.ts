import { baseApi } from './baseApi'
import type { Banner, DashboardVO, Notice } from './types'

/** 运营配置（PRD ADM-07，SUPER/OPERATOR）+ 数据大屏（ADM-08，三角色） */
export const opsApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    // ---------- 轮播 ----------
    getBanners: build.query<Banner[], void>({
      query: () => ({ url: '/banners' }),
      providesTags: ['Banner']
    }),
    createBanner: build.mutation<number, { title: string; imageUrl: string; linkUrl: string | null; sort: number }>({
      query: (body) => ({ url: '/banners', method: 'POST', data: body }),
      invalidatesTags: ['Banner']
    }),
    updateBanner: build.mutation<
      void,
      { id: number; title: string; imageUrl: string; linkUrl: string | null; sort: number; status: number }
    >({
      query: ({ id, ...body }) => ({ url: `/banners/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Banner']
    }),
    deleteBanner: build.mutation<void, number>({
      query: (id) => ({ url: `/banners/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Banner']
    }),

    // ---------- 公告 ----------
    getNotices: build.query<Notice[], void>({
      query: () => ({ url: '/notices' }),
      providesTags: ['Notice']
    }),
    createNotice: build.mutation<number, { title: string; content: string }>({
      query: (body) => ({ url: '/notices', method: 'POST', data: body }),
      invalidatesTags: ['Notice']
    }),
    updateNotice: build.mutation<void, { id: number; title: string; content: string }>({
      query: ({ id, ...body }) => ({ url: `/notices/${id}`, method: 'PUT', data: body }),
      invalidatesTags: ['Notice']
    }),
    publishNotice: build.mutation<void, number>({
      query: (id) => ({ url: `/notices/${id}/publish`, method: 'POST' }),
      invalidatesTags: ['Notice']
    }),
    offlineNotice: build.mutation<void, number>({
      query: (id) => ({ url: `/notices/${id}/offline`, method: 'POST' }),
      invalidatesTags: ['Notice']
    }),
    deleteNotice: build.mutation<void, number>({
      query: (id) => ({ url: `/notices/${id}`, method: 'DELETE' }),
      invalidatesTags: ['Notice']
    }),

    // ---------- 数据大屏 ----------
    getDashboard: build.query<DashboardVO, void>({
      query: () => ({ url: '/dashboard' }),
      providesTags: ['Dashboard']
    })
  })
})

export const {
  useGetBannersQuery,
  useCreateBannerMutation,
  useUpdateBannerMutation,
  useDeleteBannerMutation,
  useGetNoticesQuery,
  useCreateNoticeMutation,
  useUpdateNoticeMutation,
  usePublishNoticeMutation,
  useOfflineNoticeMutation,
  useDeleteNoticeMutation,
  useGetDashboardQuery
} = opsApi
