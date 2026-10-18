import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Bell, CheckCircle2, Loader2, Send, ShoppingCart, Star, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn, selectUserId } from '@/features/auth/authSlice'
import { selectUnreadNotify } from '@/features/msg/msgSlice'
import { useChatSocket } from '@/features/msg/useChatSocket'
import { baseApi } from '@/api/baseApi'
import { useLazyGetUserProfileQuery } from '@/api/authApi'
import { useLazyGetGoodsQuery } from '@/api/goodsApi'
import {
  useLazyPageConversationsQuery,
  useLazyPageMessagesQuery,
  useMarkAllNotificationsReadMutation,
  useMarkConversationReadMutation,
  useMarkNotificationReadMutation,
  usePageNotificationsQuery,
  useSendMessageMutation
} from '@/api/messageApi'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { MSG_TYPE, PAGE_SIZE } from '@/lib/constants'
import type { ConversationVO, GoodsBriefVO, MessageVO, NotificationVO } from '@/api/types'

/** 通知类型 → 图标（与 Vue 版 NOTIFY_ICONS 一致） */
const NOTIFY_ICONS: Record<string, typeof Bell> = {
  ORDER: ShoppingCart,
  AUDIT: CheckCircle2,
  REPORT: TriangleAlert,
  CREDIT: Star,
  SYSTEM: Bell
}

/** 相对时间（与 Vue 版 formatTime 逐条一致：刚刚 / x 分钟前 / HH:mm / MM-DD / yyyy-MM-dd） */
function formatTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(String(value).replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return String(value)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  if (diffMs >= 0 && diffMs < 60 * 1000) return '刚刚'
  if (diffMs >= 0 && diffMs < 60 * 60 * 1000) return `${Math.floor(diffMs / 60000)} 分钟前`
  const pad = (n: number) => String(n).padStart(2, '0')
  const sameDay =
    date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate()
  if (sameDay) return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (date.getFullYear() === now.getFullYear()) return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const nicknameInitial = (nickname?: string | null) => (nickname || '?').slice(0, 1)

/**
 * 消息中心（CHT-01~06 / NTF-01/02）：左侧会话/通知双 Tab + 右侧聊天窗（REST 拉取 + WS 实时）。
 *
 * 与 Vue 版逐条对齐的要点：
 * - 会话列表 `pageSize=100`；消息倒序分页 `pageSize=20` 转正序展示，**加载更早消息后保持视口位置**
 * - 收到非本人消息即打点已读；`CHAT_READ` 帧把本地自己发的消息标记为「已读」
 * - 发送仍走 REST（WS 只收不发）；敏感词等业务错误显示在**输入框上方红字**
 * - 商品卡消息：气泡内缩略图 + 标题 + 价格，点击跳商品详情；`content` 即 goodsId
 * - "聊一聊"入口 `?peerUserId=`（无会话时惰性草稿，首次发消息才建会话）、`?goodsId=` 待发送商品卡
 */
export default function MessageCenterPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const myUserId = useAppSelector(selectUserId)
  const unreadNotify = useAppSelector(selectUnreadNotify)

  const [activeTab, setActiveTab] = useState<'chat' | 'notify'>('chat')
  const [activeConv, setActiveConv] = useState<ConversationVO | null>(null)
  const [draftPeer, setDraftPeer] = useState<{ userId: number; nickname: string; avatar: string | null; creditLevel?: string } | null>(null)
  const [messages, setMessages] = useState<MessageVO[]>([])
  const [msgPageNum, setMsgPageNum] = useState(1)
  const [msgTotal, setMsgTotal] = useState(0)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [readConfirmedIds, setReadConfirmedIds] = useState<Set<number>>(new Set())
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [sendErrorText, setSendErrorText] = useState('')
  const [pendingGoods, setPendingGoods] = useState<GoodsBriefVO | null>(null)
  const [notifPageNum, setNotifPageNum] = useState(1)

  const flowRef = useRef<HTMLDivElement>(null)
  /** 滚动意图：bottom=贴底（首页加载/新消息）；preserve=保持视口（加载更早消息） */
  const scrollIntent = useRef<{ mode: 'bottom' | 'preserve'; prevHeight: number }>({ mode: 'bottom', prevHeight: 0 })

  const [fetchConversations, { data: convData }] = useLazyPageConversationsQuery()
  const [fetchMessages] = useLazyPageMessagesQuery()
  const [fetchPeer] = useLazyGetUserProfileQuery()
  const [fetchGoods] = useLazyGetGoodsQuery()
  const [sendMessage] = useSendMessageMutation()
  const [markConversationRead] = useMarkConversationReadMutation()
  const [markNotificationRead] = useMarkNotificationReadMutation()
  const [markAllNotificationsRead, { isLoading: markingAll }] = useMarkAllNotificationsReadMutation()

  const { data: notifData, isFetching: notifLoading, refetch: refetchNotifs } = usePageNotificationsQuery(
    { pageNum: notifPageNum, pageSize: PAGE_SIZE.notifications },
    { skip: !isLoggedIn }
  )

  const conversations = convData?.list ?? []
  const notifications = notifData?.list ?? []
  const notifTotal = notifData?.total ?? 0
  const hasMoreMessages = messages.length < msgTotal
  const totalChatUnread = conversations.reduce((sum, c) => sum + (c.unreadCount || 0), 0)

  const activePeer = activeConv
    ? { userId: activeConv.peerUserId, nickname: activeConv.peerNickname, avatar: activeConv.peerAvatar, creditLevel: undefined as string | undefined }
    : draftPeer ?? { userId: 0, nickname: '', avatar: null, creditLevel: undefined }

  /** 红点刷新（等价旧版 `msgStore.refresh()`）：让未读计数与列表重新拉取 */
  const refreshUnread = useCallback(() => {
    dispatch(baseApi.util.invalidateTags(['Conversation', 'Notification']))
  }, [dispatch])

  const refreshConversations = useCallback(() => {
    void fetchConversations({ pageNum: 1, pageSize: PAGE_SIZE.conversations })
    refreshUnread()
  }, [fetchConversations, refreshUnread])

  // ---- 滚动：state 提交后同步（等价 Vue `await nextTick()` 后设置 scrollTop） ----
  useLayoutEffect(() => {
    const el = flowRef.current
    if (!el) return
    if (scrollIntent.current.mode === 'bottom') el.scrollTop = el.scrollHeight
    else el.scrollTop = el.scrollHeight - scrollIntent.current.prevHeight
  }, [messages])

  const loadMessages = useCallback(
    async (conv: ConversationVO, pageNum: number) => {
      setMessagesLoading(true)
      try {
        const res = await fetchMessages({ conversationId: conv.id, pageNum, pageSize: PAGE_SIZE.messages }).unwrap()
        setMsgTotal(res.total)
        const ascList = [...res.list].reverse()
        const el = flowRef.current
        if (pageNum === 1) {
          scrollIntent.current = { mode: 'bottom', prevHeight: 0 }
          setMessages(ascList)
        } else {
          scrollIntent.current = { mode: 'preserve', prevHeight: el?.scrollHeight ?? 0 }
          setMessages((prev) => [...ascList, ...prev])
        }
      } catch {
        /* baseQuery 已提示 */
      } finally {
        setMessagesLoading(false)
      }
    },
    [fetchMessages]
  )

  const openConversation = useCallback(
    async (conv: ConversationVO) => {
      setActiveConv({ ...conv })
      setDraftPeer(null)
      setMsgPageNum(1)
      setMessages([])
      setMsgTotal(0)
      setReadConfirmedIds(new Set())
      setSendErrorText('')
      await loadMessages(conv, 1)
      // 拉取即已读（后端同事务打点 read_at），随后刷新列表与红点
      try {
        await markConversationRead(conv.id).unwrap()
      } catch {
        /* 打点失败不阻塞展示 */
      }
      refreshConversations()
    },
    [loadMessages, markConversationRead, refreshConversations]
  )

  // ---- 首屏：会话 + 通知 + 入口参数（peerUserId / goodsId） ----
  useEffect(() => {
    if (!isLoggedIn) return
    void fetchConversations({ pageNum: 1, pageSize: PAGE_SIZE.conversations })
    refreshUnread()
  }, [isLoggedIn, fetchConversations, refreshUnread])

  useEffect(() => {
    if (!isLoggedIn || !convData) return
    const peerUserId = Number(searchParams.get('peerUserId'))
    if (!peerUserId || peerUserId === myUserId) return
    const existing = (convData.list ?? []).find((conv) => conv.peerUserId === peerUserId)
    if (existing) {
      void openConversation(existing)
      return
    }
    // 尚无会话：先建"草稿对端"，首次发消息才真正建会话（CHT-01 惰性）
    setDraftPeer({ userId: peerUserId, nickname: '对方', avatar: null })
    fetchPeer(peerUserId)
      .unwrap()
      .then((detail) => {
        setDraftPeer((prev) =>
          prev && prev.userId === peerUserId
            ? { userId: peerUserId, nickname: detail.nickname || '对方', avatar: detail.avatar, creditLevel: detail.creditLevel }
            : prev
        )
      })
      .catch(() => {})
    // 只在首次拿到会话列表时处理入口参数
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, convData, myUserId, searchParams])

  useEffect(() => {
    const goodsId = Number(searchParams.get('goodsId'))
    if (!goodsId || !isLoggedIn) return
    fetchGoods(goodsId)
      .unwrap()
      .then((detail) => {
        setPendingGoods({
          goodsId,
          title: detail.title,
          price: Number(detail.price),
          coverUrl: detail.images?.[0]?.thumbUrl ?? null
        })
      })
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn, searchParams])

  const appendMessage = useCallback((message: MessageVO) => {
    scrollIntent.current = { mode: 'bottom', prevHeight: 0 }
    setMessages((prev) => (prev.some((item) => item.id === message.id) ? prev : [...prev, message]))
  }, [])

  // ---- WebSocket 实时 ----
  const handleWsNew = useCallback(
    (message: MessageVO) => {
      refreshConversations()
      if (activeConv && message.conversationId === activeConv.id) {
        appendMessage(message)
        if (message.senderId !== myUserId) {
          // 聊天窗打开时到达的新消息立即打点已读（CHT-05）
          markConversationRead(activeConv.id)
            .unwrap()
            .catch(() => {})
        }
        return
      }
      // 非当前会话：列表已通过 refetch 更新（含未读自增），此处不再本地改动
    },
    [activeConv, appendMessage, markConversationRead, myUserId, refreshConversations]
  )

  const handleWsRead = useCallback(
    (payload: { conversationId: number; readerId: number }) => {
      if (activeConv && payload.conversationId === activeConv.id) {
        setReadConfirmedIds((prev) => {
          const next = new Set(prev)
          messages.filter((msg) => msg.senderId === myUserId).forEach((msg) => next.add(msg.id))
          return next
        })
      }
    },
    [activeConv, messages, myUserId]
  )

  useChatSocket({ enabled: isLoggedIn, onNewMessage: handleWsNew, onReadReceipt: handleWsRead })

  // ---- 发送 ----
  const sendText = async () => {
    const content = draft.trim()
    if (!content) return
    if (!activePeer.userId || activePeer.userId === myUserId) return
    setSending(true)
    setSendErrorText('')
    try {
      const created = await sendMessage({ peerUserId: activePeer.userId, msgType: MSG_TYPE.TEXT, content }).unwrap()
      appendMessage(created)
      setDraft('')
      await syncAfterSend(created)
    } catch (error) {
      // 敏感词命中（GOODS_SENSITIVE）等：输入框上方红字提示
      setSendErrorText((error as Error)?.message || '发送失败')
    } finally {
      setSending(false)
    }
  }

  const sendGoodsCard = async () => {
    if (!pendingGoods || !activePeer.userId) return
    setSending(true)
    setSendErrorText('')
    try {
      const created = await sendMessage({
        peerUserId: activePeer.userId,
        msgType: MSG_TYPE.GOODS_CARD,
        content: String(pendingGoods.goodsId)
      }).unwrap()
      appendMessage(created)
      setPendingGoods(null)
      await syncAfterSend(created)
    } catch (error) {
      setSendErrorText((error as Error)?.message || '发送失败')
    } finally {
      setSending(false)
    }
  }

  /** 发送成功后同步左侧会话摘要（新会话则整表刷新，以便拿到对端头像/昵称） */
  const syncAfterSend = async (message: MessageVO) => {
    if (!activeConv) {
      const res = await fetchConversations({ pageNum: 1, pageSize: PAGE_SIZE.conversations }).unwrap()
      const conv = (res.list ?? []).find((item) => item.id === message.conversationId)
      if (conv) setActiveConv({ ...conv })
      setDraftPeer(null)
      return
    }
    refreshConversations()
  }

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key !== 'Enter' || e.shiftKey) return
    e.preventDefault()
    void sendText()
  }

  const isMessageRead = (message: MessageVO) => !!message.readAt || readConfirmedIds.has(message.id)

  const goGoods = (message: MessageVO) => {
    const goodsId = message.goods?.goodsId || Number(message.content)
    if (goodsId) navigate(`/goods/${goodsId}`)
  }

  const readNotification = async (item: NotificationVO) => {
    if (item.isRead !== 0) return
    await markNotificationRead(item.id)
    refreshUnread()
  }

  const readAllNotifications = async () => {
    await markAllNotificationsRead().unwrap()
    refreshUnread()
    toast.success('通知已全部标记为已读')
  }

  return (
    <div className="page">
      <h2 className="page-title">消息中心</h2>

      <div className="flex gap-4" style={{ height: 'calc(100vh - 200px)', minHeight: 480 }}>
        {/* 左：会话 / 通知 */}
        <aside className="paper-card flex w-[320px] shrink-0 flex-col overflow-hidden p-2">
          <div className="flex gap-1 border-b border-border px-1">
            {([
              { key: 'chat', label: '聊天', count: totalChatUnread },
              { key: 'notify', label: '通知', count: unreadNotify }
            ] as const).map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                className={cn(
                  'relative flex items-center gap-1.5 px-3.5 py-2.5 text-[13px] font-semibold transition',
                  activeTab === tab.key ? 'text-primary' : 'text-muted-foreground hover:text-primary'
                )}
              >
                {tab.label}
                {tab.count > 0 ? (
                  <span className="grid h-4 min-w-4 place-items-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground">
                    {tab.count > 99 ? '99+' : tab.count}
                  </span>
                ) : null}
                <span
                  className={cn(
                    'absolute inset-x-2.5 -bottom-px h-0.5 rounded-full',
                    activeTab === tab.key ? 'bg-primary' : 'bg-transparent'
                  )}
                />
              </button>
            ))}
          </div>

          {activeTab === 'chat' ? (
            <div className="mt-2 flex-1 overflow-y-auto">
              {!conversations.length ? (
                <p className="px-3 py-6 text-center text-[13px] text-muted-foreground">
                  还没有会话，去商品页找卖家聊聊吧
                </p>
              ) : (
                conversations.map((conv) => {
                  const active = activeConv ? conv.id === activeConv.id : !!draftPeer && conv.peerUserId === draftPeer.userId
                  return (
                    <button
                      key={conv.id}
                      type="button"
                      onClick={() => void openConversation(conv)}
                      className={cn(
                        'relative flex w-full items-center gap-3 rounded-lg px-2.5 py-2.5 text-left transition',
                        active ? 'bg-secondary/70' : 'hover:bg-secondary/40'
                      )}
                    >
                      <span className="relative shrink-0">
                        {conv.peerAvatar ? (
                          <img src={conv.peerAvatar} alt="" className="size-[42px] rounded-full object-cover" />
                        ) : (
                          <span className="grid size-[42px] place-items-center rounded-full bg-secondary text-[14px] text-primary">
                            {nicknameInitial(conv.peerNickname)}
                          </span>
                        )}
                        {conv.unreadCount > 0 ? (
                          <span className="absolute -top-1 -right-1 grid h-4 min-w-4 place-items-center rounded-full bg-destructive px-1 text-[10px] font-semibold text-destructive-foreground">
                            {conv.unreadCount > 99 ? '99+' : conv.unreadCount}
                          </span>
                        ) : null}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center justify-between gap-2">
                          <span className="truncate text-[13px] font-semibold text-foreground">{conv.peerNickname}</span>
                          <span className="shrink-0 text-[11px] text-muted-foreground">{formatTime(conv.lastMsgAt)}</span>
                        </span>
                        <span className="mt-0.5 block truncate text-[12px] text-muted-foreground">
                          {conv.lastMsg || '新的会话'}
                        </span>
                      </span>
                    </button>
                  )
                })
              )}
            </div>
          ) : (
            <>
              <div className="mt-1 flex justify-end px-1">
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={unreadNotify === 0 || markingAll}
                  onClick={readAllNotifications}
                >
                  全部已读
                </Button>
              </div>
              <div className="flex-1 overflow-y-auto">
                {!notifLoading && !notifications.length ? (
                  <p className="px-3 py-6 text-center text-[13px] text-muted-foreground">暂无通知</p>
                ) : (
                  notifications.map((item) => {
                    const Icon = NOTIFY_ICONS[item.type] || Bell
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => void readNotification(item)}
                        className={cn(
                          'flex w-full items-start gap-2.5 rounded-lg px-2.5 py-2.5 text-left transition',
                          item.isRead === 0 ? 'bg-secondary/40' : 'hover:bg-secondary/30'
                        )}
                      >
                        <Icon className="mt-0.5 size-4 shrink-0 text-primary" />
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center justify-between gap-2">
                            <span className="truncate text-[13px] font-semibold text-foreground">{item.title}</span>
                            <span className="shrink-0 text-[11px] text-muted-foreground">{formatTime(item.createdAt)}</span>
                          </span>
                          <span className="mt-0.5 block text-[12px] leading-relaxed text-muted-foreground">
                            {item.content}
                          </span>
                        </span>
                        {item.isRead === 0 ? <span className="mt-1.5 size-2 shrink-0 rounded-full bg-destructive" /> : null}
                      </button>
                    )
                  })
                )}
              </div>
              {notifTotal > PAGE_SIZE.notifications ? (
                <div className="flex items-center justify-center gap-2 border-t border-border py-2">
                  <Button size="sm" variant="outline" disabled={notifPageNum <= 1} onClick={() => setNotifPageNum((p) => p - 1)}>
                    上一页
                  </Button>
                  <span className="text-[12px] text-muted-foreground tabular-nums">
                    {notifPageNum} / {Math.max(1, Math.ceil(notifTotal / PAGE_SIZE.notifications))}
                  </span>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={notifPageNum >= Math.ceil(notifTotal / PAGE_SIZE.notifications)}
                    onClick={() => setNotifPageNum((p) => p + 1)}
                  >
                    下一页
                  </Button>
                </div>
              ) : null}
            </>
          )}
        </aside>

        {/* 右：聊天窗 / 通知占位 */}
        <main className="paper-card flex min-w-0 flex-1 flex-col overflow-hidden">
          {activeTab === 'chat' ? (
            activePeer.userId ? (
              <>
                <header className="flex items-center gap-2.5 border-b border-border px-4 py-3">
                  {activePeer.avatar ? (
                    <img src={activePeer.avatar} alt="" className="size-8 rounded-full object-cover" />
                  ) : (
                    <span className="grid size-8 place-items-center rounded-full bg-secondary text-[12px] text-primary">
                      {nicknameInitial(activePeer.nickname)}
                    </span>
                  )}
                  <span className="text-[14px] font-semibold text-foreground">{activePeer.nickname}</span>
                  {activePeer.creditLevel ? <Badge variant="success">{activePeer.creditLevel}</Badge> : null}
                </header>

                <div ref={flowRef} className="flex-1 overflow-y-auto px-4 py-4">
                  {hasMoreMessages ? (
                    <div className="mb-3 text-center">
                      <Button size="sm" variant="ghost" disabled={messagesLoading} onClick={() => {
                        const next = msgPageNum + 1
                        setMsgPageNum(next)
                        if (activeConv) void loadMessages(activeConv, next)
                      }}>
                        {messagesLoading ? <Loader2 className="size-3.5 animate-spin" /> : null}
                        加载更早消息
                      </Button>
                    </div>
                  ) : null}

                  {!messagesLoading && !messages.length ? (
                    <p className="py-10 text-center text-[13px] text-muted-foreground">打个招呼吧～</p>
                  ) : null}

                  <div className="space-y-3">
                    {messages.map((msg) => {
                      const mine = msg.senderId === myUserId
                      return (
                        <div key={msg.id} className={cn('flex flex-col', mine ? 'items-end' : 'items-start')}>
                          {msg.msgType === MSG_TYPE.GOODS_CARD ? (
                            <button
                              type="button"
                              onClick={() => goGoods(msg)}
                              className="coupon-card flex w-[240px] items-center gap-3 p-2.5 text-left transition hover:-translate-y-0.5"
                            >
                              {msg.goods?.coverUrl ? (
                                <img src={msg.goods.coverUrl} alt="" className="size-14 rounded-lg object-cover" />
                              ) : (
                                <span className="grid size-14 place-items-center rounded-lg bg-secondary/60 text-[20px]">🛒</span>
                              )}
                              <span className="min-w-0">
                                <span className="block truncate text-[13px] font-semibold text-foreground">
                                  {msg.goods?.title || '商品已删除'}
                                </span>
                                <span className="mt-1 block text-[13px] font-bold text-[var(--terracotta)]">
                                  ¥{msg.goods?.price ?? '--'}
                                </span>
                              </span>
                            </button>
                          ) : (
                            <>
                              <div
                                className={cn(
                                  'max-w-[70%] rounded-xl px-3.5 py-2.5 text-[13px] leading-relaxed break-words whitespace-pre-wrap',
                                  mine
                                    ? 'bg-primary text-primary-foreground'
                                    : 'border border-border bg-card text-foreground'
                                )}
                              >
                                {msg.content}
                              </div>
                              {mine ? (
                                <span className="mt-1 text-[11px] text-muted-foreground">
                                  {isMessageRead(msg) ? '已读' : '未读'}
                                </span>
                              ) : null}
                            </>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>

                <footer className="border-t border-border px-4 py-3">
                  {pendingGoods ? (
                    <div className="mb-2.5 flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-border p-2.5">
                      {pendingGoods.coverUrl ? (
                        <img src={pendingGoods.coverUrl} alt="" className="size-11 rounded-lg object-cover" />
                      ) : (
                        <span className="grid size-11 place-items-center rounded-lg bg-secondary/60">🛒</span>
                      )}
                      <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-foreground">
                        {pendingGoods.title}
                      </span>
                      <Button size="sm" disabled={sending} onClick={sendGoodsCard}>
                        作为卡片发送
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setPendingGoods(null)}>
                        移除
                      </Button>
                    </div>
                  ) : null}

                  {sendErrorText ? <p className="mb-2 text-[12px] text-destructive">{sendErrorText}</p> : null}

                  <div className="flex items-end gap-3">
                    <Textarea
                      rows={2}
                      maxLength={500}
                      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                      className="resize-none"
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      onKeyDown={onKeyDown}
                    />
                    <Button disabled={sending} onClick={sendText}>
                      <Send className="size-4" />
                      发送
                    </Button>
                  </div>
                </footer>
              </>
            ) : (
              <div className="grid flex-1 place-items-center px-6 text-center text-[13px] text-muted-foreground">
                选择左侧会话开始聊天
              </div>
            )
          ) : (
            <div className="grid flex-1 place-items-center px-6 text-center text-[13px] text-muted-foreground">
              通知列表见左侧，点击未读通知即可标记已读
            </div>
          )}
        </main>
      </div>
    </div>
  )
}
