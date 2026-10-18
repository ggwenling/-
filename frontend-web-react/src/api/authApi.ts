import { baseApi } from './baseApi'
import type {
  CreditLog,
  LoginDTO,
  LoginVO,
  PageResult,
  RegisterDTO,
  SendVerifyCodeDTO,
  UserDetailVO,
  UserEditDTO,
  VerifyCheckDTO
} from './types'

/**
 * 认证与用户接口（契约见 docs/contracts-frontend-web.md §1.1）。
 * 注意：`GET /credits/my` 的 pageSize 上限是 50（`@Max(50)`），`GET /reviews` 的 `revieweeId` 必填。
 */
export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    /** 登录（失败 5 次锁 10 分钟 + 同 IP 限流 5 次/分钟） */
    login: build.mutation<LoginVO, LoginDTO>({
      query: (body) => ({ url: '/auth/login', method: 'POST', data: body })
    }),
    /** 注册并自动登录 */
    register: build.mutation<LoginVO, RegisterDTO>({
      query: (body) => ({ url: '/auth/register', method: 'POST', data: body })
    }),
    /** 登出（客户端丢弃 token） */
    logout: build.mutation<void, void>({
      query: () => ({ url: '/auth/logout', method: 'POST' }),
      invalidatesTags: ['Me', 'Conversation', 'Notification']
    }),
    /** 当前登录用户资料（刷新页面恢复登录态用） */
    getMe: build.query<UserDetailVO, void>({
      query: () => ({ url: '/users/me' }),
      providesTags: ['Me']
    }),
    /** 用户主页资料（聊天窗对端信息等） */
    getUserProfile: build.query<UserDetailVO, number>({
      query: (userId) => ({ url: `/users/${userId}` })
    }),
    /** 编辑个人资料（昵称/头像/学院/简介） */
    updateProfile: build.mutation<void, UserEditDTO>({
      query: (body) => ({ url: '/users/me', method: 'PUT', data: body }),
      invalidatesTags: ['Me']
    }),
    /** 我的信用流水（pageSize 上限 50） */
    getMyCredits: build.query<PageResult<CreditLog>, { pageNum: number; pageSize: number }>({
      query: ({ pageNum, pageSize }) => ({ url: '/credits/my', params: { pageNum, pageSize } }),
      providesTags: ['Credit']
    }),
    /** 发送校园认证验证码（演示模式打印到后端控制台） */
    sendVerifyCode: build.mutation<void, SendVerifyCodeDTO>({
      query: (body) => ({ url: '/auth/verify-code/send', method: 'POST', data: body })
    }),
    /** 提交验证码完成校园认证 */
    verifyCode: build.mutation<void, VerifyCheckDTO>({
      query: (body) => ({ url: '/auth/verify-code/check', method: 'POST', data: body }),
      invalidatesTags: ['Me']
    })
  })
})

export const {
  useLoginMutation,
  useRegisterMutation,
  useLogoutMutation,
  useGetMeQuery,
  useLazyGetMeQuery,
  useGetUserProfileQuery,
  useLazyGetUserProfileQuery,
  useUpdateProfileMutation,
  useGetMyCreditsQuery,
  useLazyGetMyCreditsQuery,
  useSendVerifyCodeMutation,
  useVerifyCodeMutation
} = authApi
