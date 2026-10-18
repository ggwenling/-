import { baseApi } from './baseApi'
import type {
  ChatUnreadVO,
  ConversationVO,
  MessageSendDTO,
  MessageVO,
  NotificationVO,
  PageResult
} from './types'

/**
 * 消息与通知接口（契约见 docs/contracts-frontend-web.md §1.4）。
 *
 * 关键口径：
 * - `GET /chats/conversations` 的 pageSize 上限 100（Vue 版固定传 100、无分页）
 * - `GET /chats/conversations/{id}/messages` **后端按 createdAt 倒序**返回，页面需自行 reverse
 * - `GET /notifications/unread-count` 的 data 是**裸数字**（与 `/chats/unread` 的对象形状不同）
 * - 发送消息后：`Conversation`（未读/排序）与 `Message`（当前会话）都要失效
 */
export const messageApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    /** 未读聚合（导航红点数据源：未读消息总数） */
    getChatUnread: build.query<ChatUnreadVO, void>({
      query: () => ({ url: '/chats/unread' }),
      providesTags: ['Conversation']
    }),
    /** 未读通知数（data 为裸数字） */
    getNotificationUnreadCount: build.query<number, void>({
      query: () => ({ url: '/notifications/unread-count' }),
      providesTags: ['Notification']
    }),
    /** 会话列表（按最后消息倒序） */
    pageConversations: build.query<PageResult<ConversationVO>, { pageNum: number; pageSize: number }>({
      query: ({ pageNum, pageSize }) => ({ url: '/chats/conversations', params: { pageNum, pageSize } }),
      providesTags: ['Conversation']
    }),
    /** 某会话的消息分页（后端倒序） */
    pageMessages: build.query<PageResult<MessageVO>, { conversationId: number; pageNum: number; pageSize: number }>({
      query: ({ conversationId, pageNum, pageSize }) => ({
        url: `/chats/conversations/${conversationId}/messages`,
        params: { pageNum, pageSize }
      }),
      providesTags: ['Message']
    }),
    /** 发送消息（TEXT / GOODS_CARD），返回落库后的 MessageVO */
    sendMessage: build.mutation<MessageVO, MessageSendDTO>({
      query: (body) => ({ url: '/chats/messages', method: 'POST', data: body }),
      invalidatesTags: ['Conversation']
    }),
    /** 标记会话已读（打开会话 / 收到新消息时打点） */
    markConversationRead: build.mutation<void, number>({
      query: (conversationId) => ({ url: `/chats/conversations/${conversationId}/read`, method: 'POST' }),
      invalidatesTags: ['Conversation']
    }),
    /** 通知列表（type 白名单：ORDER/AUDIT/REPORT/CREDIT/SYSTEM） */
    pageNotifications: build.query<
      PageResult<NotificationVO>,
      { pageNum: number; pageSize: number; type?: string }
    >({
      query: ({ pageNum, pageSize, type }) => ({ url: '/notifications', params: { pageNum, pageSize, type } }),
      providesTags: ['Notification']
    }),
    /** 单条通知已读 */
    markNotificationRead: build.mutation<void, number>({
      query: (id) => ({ url: `/notifications/${id}/read`, method: 'POST' }),
      invalidatesTags: ['Notification']
    }),
    /** 全部通知已读 */
    markAllNotificationsRead: build.mutation<void, void>({
      query: () => ({ url: '/notifications/read-all', method: 'POST' }),
      invalidatesTags: ['Notification']
    })
  })
})

export const {
  useGetChatUnreadQuery,
  useGetNotificationUnreadCountQuery,
  usePageConversationsQuery,
  useLazyPageConversationsQuery,
  usePageMessagesQuery,
  useLazyPageMessagesQuery,
  useSendMessageMutation,
  useMarkConversationReadMutation,
  usePageNotificationsQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation
} = messageApi
