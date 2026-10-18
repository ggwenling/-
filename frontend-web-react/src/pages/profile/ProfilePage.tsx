import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Star } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectUserInfo } from '@/features/auth/authSlice'
import { useLazyGetMyCreditsQuery, useUpdateProfileMutation } from '@/api/authApi'
import { useLazyPageMyFavoritesQuery, useLazyPageMyGoodsQuery } from '@/api/goodsApi'
import { useLazyPageOrdersQuery, useLazyPageReviewsQuery } from '@/api/orderApi'
import { useLazyPageMyReportsQuery } from '@/api/reportApi'
import { uploadImage } from '@/api/upload'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { EmptyState } from '@/components/EmptyState'
import { GoodsCard } from '@/components/GoodsCard'
import { PriceText } from '@/components/PriceText'
import {
  CREDIT_REASON_TEXT,
  REPORT_STATUS_TEXT,
  REPORT_TARGET_TEXT,
  REPORT_TYPE_TEXT,
  creditLevel,
  PAGE_SIZE,
  UPLOAD_ACCEPT
} from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { CreditLog, FavoriteVO, GoodsCardVO, OrderListVO, ReportVO, ReviewVO } from '@/api/types'

type TabKey = 'goods' | 'favorites' | 'bought' | 'sold' | 'reviews' | 'credits' | 'reports'

/** 七个 Tab（与 Vue 版 ProfileView 的 el-tab-pane 顺序、文案逐字一致） */
const TABS: { key: TabKey; label: string }[] = [
  { key: 'goods', label: '我的商品' },
  { key: 'favorites', label: '收藏' },
  { key: 'bought', label: '买到的' },
  { key: 'sold', label: '卖出的' },
  { key: 'reviews', label: '收到的评价' },
  { key: 'credits', label: '信用记录' },
  { key: 'reports', label: '我的举报' }
]

/** 订单状态文案/色调（与 Vue 版 ORDER_TEXT / ORDER_TAG 一致） */
const ORDER_TEXT: Record<string, string> = {
  WAIT_CONFIRM: '待确认',
  SCHEDULED: '待面交',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}
const ORDER_TONE: Record<string, 'warning' | 'default' | 'success' | 'soft'> = {
  WAIT_CONFIRM: 'warning',
  SCHEDULED: 'default',
  COMPLETED: 'success',
  CANCELLED: 'soft'
}

const shortTime = (t?: string | null) => (t ? String(t).slice(5, 16) : '')

/**
 * 个人中心（PRD USR-04~06 / GDS-06 / ORD-05 / CRD-03 / §5.7）：左个人卡 + 右七 Tab（懒加载）。
 *
 * 与 Vue 版对齐：
 * - 信用分进度 = `min(max(score,0),150)/150`；四档文案与色调与旧版一致
 * - Tab **首次进入才拉取**并缓存（`loaded` 集合）；订单按 10/页、信用按 10/页、举报按 20/页分页
 * - 「收到的评价」用 `revieweeId = 当前用户 id`；空内容显示「默认好评」
 * - 收藏列表需 **filter(goods)**（后端 `FavoriteVO.goods` 可为 null）
 * - 头像上传走 `POST /uploads`（字段 `file`，≤5MB 提示，无前端体积校验 —— 与旧版一致）
 */
export default function ProfilePage() {
  const navigate = useNavigate()
  const profile = useAppSelector(selectUserInfo)

  const [activeTab, setActiveTab] = useState<TabKey>('goods')
  const loadedRef = useRef<Set<string>>(new Set())
  const [loadingTab, setLoadingTab] = useState<Record<string, boolean>>({})

  const [myGoods, setMyGoods] = useState<GoodsCardVO[]>([])
  const [favorites, setFavorites] = useState<FavoriteVO[]>([])
  const [orderRows, setOrderRows] = useState<OrderListVO[]>([])
  const [orderTotal, setOrderTotal] = useState(0)
  const [orderPage, setOrderPage] = useState(1)
  const [reviewList, setReviewList] = useState<ReviewVO[]>([])
  const [creditList, setCreditList] = useState<CreditLog[]>([])
  const [creditTotal, setCreditTotal] = useState(0)
  const [creditPage, setCreditPage] = useState(1)
  const [reportList, setReportList] = useState<ReportVO[]>([])
  const [reportTotal, setReportTotal] = useState(0)
  const [reportPage, setReportPage] = useState(1)
  const [reportStatus, setReportStatus] = useState<'' | 0 | 1 | 2>('')

  const [fetchGoods] = useLazyPageMyGoodsQuery()
  const [fetchFavorites] = useLazyPageMyFavoritesQuery()
  const [fetchOrders] = useLazyPageOrdersQuery()
  const [fetchReviews] = useLazyPageReviewsQuery()
  const [fetchCredits] = useLazyGetMyCreditsQuery()
  const [fetchReports] = useLazyPageMyReportsQuery()
  const [updateProfile, { isLoading: saving }] = useUpdateProfileMutation()

  const [editOpen, setEditOpen] = useState(false)
  const [editForm, setEditForm] = useState({ nickname: '', avatar: '', college: '', bio: '' })
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const avatarInputRef = useRef<HTMLInputElement>(null)

  const creditScore = profile?.creditScore ?? 0
  const creditPercent = Math.round((Math.min(Math.max(creditScore, 0), 150) / 150) * 100)
  const level = creditLevel(creditScore)

  const withLoading = async (key: string, fn: () => Promise<void>) => {
    setLoadingTab((prev) => ({ ...prev, [key]: true }))
    try {
      await fn()
    } finally {
      setLoadingTab((prev) => ({ ...prev, [key]: false }))
    }
  }

  const loadGoods = () =>
    withLoading('goods', async () => {
      if (loadedRef.current.has('goods')) return
      const res = await fetchGoods({ pageNum: 1, pageSize: 50 }).unwrap()
      setMyGoods(res.list ?? [])
      loadedRef.current.add('goods')
    })

  const loadFavorites = () =>
    withLoading('favorites', async () => {
      if (loadedRef.current.has('favorites')) return
      const res = await fetchFavorites({ pageNum: 1, pageSize: 50 }).unwrap()
      setFavorites((res.list ?? []).filter((f: FavoriteVO) => f.goods))
      loadedRef.current.add('favorites')
    })

  const loadOrders = (tab: 'bought' | 'sold', page: number) =>
    withLoading(tab, async () => {
      const res = await fetchOrders({ role: tab === 'bought' ? 'buyer' : 'seller', pageNum: page, pageSize: 10 }).unwrap()
      setOrderRows(res.list ?? [])
      setOrderTotal(res.total ?? 0)
      setOrderPage(page)
      loadedRef.current.add(tab)
    })

  const loadReviews = () =>
    withLoading('reviews', async () => {
      if (loadedRef.current.has('reviews') || !profile?.userId) return
      const res = await fetchReviews({ revieweeId: profile.userId, pageNum: 1, pageSize: 20 }).unwrap()
      setReviewList(res.list ?? [])
      loadedRef.current.add('reviews')
    })

  const loadCredits = (page: number) =>
    withLoading('credits', async () => {
      const res = await fetchCredits({ pageNum: page, pageSize: 10 }).unwrap()
      setCreditList(res.list ?? [])
      setCreditTotal(res.total ?? 0)
      setCreditPage(page)
    })

  const loadReports = (page: number) =>
    withLoading('reports', async () => {
      const res = await fetchReports({
        ...(reportStatus === '' ? {} : { status: reportStatus }),
        pageNum: page,
        pageSize: PAGE_SIZE.profileReports
      }).unwrap()
      setReportList(res.list ?? [])
      setReportTotal(res.total ?? 0)
      setReportPage(page)
    })

  // Tab 懒加载（等价旧版 `watch(activeTab, …, { immediate: true })`）
  useEffect(() => {
    if (!profile?.userId) return
    if (activeTab === 'goods') void loadGoods()
    else if (activeTab === 'favorites') void loadFavorites()
    else if (activeTab === 'bought' || activeTab === 'sold') void loadOrders(activeTab, 1)
    else if (activeTab === 'reviews') void loadReviews()
    else if (activeTab === 'credits') void loadCredits(1)
    else if (activeTab === 'reports') void loadReports(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, profile?.userId])

  const openEdit = () => {
    setEditForm({
      nickname: profile?.nickname || '',
      avatar: profile?.avatar || '',
      college: profile?.college || '',
      bio: profile?.bio || ''
    })
    setEditOpen(true)
  }

  const onAvatarPicked = async (files: FileList | null) => {
    const file = files?.[0]
    if (!file) return
    setUploadingAvatar(true)
    try {
      const uploaded = await uploadImage(file)
      if (uploaded?.url) {
        setEditForm((prev) => ({ ...prev, avatar: uploaded.url }))
        toast.success('头像已上传')
      }
    } catch {
      /* uploadImage 已按错误码提示 */
    } finally {
      setUploadingAvatar(false)
      if (avatarInputRef.current) avatarInputRef.current.value = ''
    }
  }

  const saveProfile = async () => {
    if (!editForm.nickname) {
      toast.warning('昵称不能为空')
      return
    }
    try {
      await updateProfile(editForm).unwrap()
      toast.success('资料已更新')
      setEditOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const orderPager = (tab: 'bought' | 'sold') =>
    orderTotal > 10 ? (
      <div className="mt-4 flex items-center justify-center gap-3">
        <Button size="sm" variant="outline" disabled={orderPage <= 1} onClick={() => void loadOrders(tab, orderPage - 1)}>
          上一页
        </Button>
        <span className="text-[12px] text-muted-foreground tabular-nums">
          {orderPage} / {Math.max(1, Math.ceil(orderTotal / 10))}
        </span>
        <Button
          size="sm"
          variant="outline"
          disabled={orderPage >= Math.ceil(orderTotal / 10)}
          onClick={() => void loadOrders(tab, orderPage + 1)}
        >
          下一页
        </Button>
      </div>
    ) : null

  return (
    <div className="page grid grid-cols-1 gap-5 lg:grid-cols-[300px_1fr]">
      {/* 左：个人卡 */}
      <Card className="paper-card h-fit p-5">
        <div className="flex items-center gap-3.5">
          {profile?.avatar ? (
            <img src={profile.avatar} alt="" className="size-[72px] rounded-full border border-border object-cover" />
          ) : (
            <span className="grid size-[72px] place-items-center rounded-full bg-secondary text-[26px] text-primary">
              {(profile?.nickname || '我').slice(0, 1)}
            </span>
          )}
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="truncate text-[16px] font-bold text-foreground">{profile?.nickname || '—'}</span>
              {profile?.authStatus === 1 ? <Badge variant="success">校园已认证</Badge> : <Badge variant="soft">未认证</Badge>}
            </div>
            <Badge variant={level.tone} className="mt-1.5">
              信用 {level.text}
            </Badge>
          </div>
        </div>

        <div className="mt-4">
          <div className="flex items-baseline gap-2">
            <span className="text-[12px] text-muted-foreground">信用分</span>
            <b className="text-[22px] font-bold text-foreground tabular-nums">{profile?.creditScore ?? '—'}</b>
            <span className="text-[12px] text-muted-foreground">/ 150</span>
          </div>
          <Progress value={creditPercent} className="mt-2" />
          <p className="mt-2 text-[11px] leading-relaxed text-muted-foreground">
            优秀 ≥120 · 良好 80~119 · 一般 60~79 · 受限 &lt;60（PRD §5.7）
          </p>
        </div>

        <div className="mt-4 space-y-2 border-t border-dashed border-border pt-4 text-[13px]">
          {[
            { label: '学号', value: profile?.studentNoMasked || '未认证' },
            { label: '校园邮箱', value: profile?.campusEmailMasked || '未认证' },
            { label: '学院', value: profile?.college || '—' },
            { label: '简介', value: profile?.bio || '—' },
            { label: '注册时间', value: profile?.createdAt || '—' }
          ].map((row) => (
            <div key={row.label} className="flex items-start justify-between gap-3">
              <span className="shrink-0 text-muted-foreground">{row.label}</span>
              <span className="text-right break-all text-foreground">{row.value}</span>
            </div>
          ))}
        </div>

        <div className="mt-4 flex flex-wrap gap-2.5">
          <Button variant="outline" onClick={openEdit}>
            编辑资料
          </Button>
          {profile?.authStatus !== 1 ? <Button onClick={() => navigate('/verify')}>去校园认证</Button> : null}
        </div>
      </Card>

      {/* 右：七 Tab */}
      <Card className="paper-card p-5">
        <div className="flex flex-wrap gap-1 border-b border-border">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={cn(
                'relative px-3.5 py-2.5 text-[13px] font-semibold transition',
                activeTab === tab.key ? 'text-primary' : 'text-muted-foreground hover:text-primary'
              )}
            >
              {tab.label}
              <span
                className={cn(
                  'absolute inset-x-2.5 -bottom-px h-0.5 rounded-full',
                  activeTab === tab.key ? 'bg-primary' : 'bg-transparent'
                )}
              />
            </button>
          ))}
        </div>

        <div className="pt-5">
          {/* 我的商品 */}
          {activeTab === 'goods' ? (
            loadingTab.goods ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-48 w-full rounded-xl" />
                ))}
              </div>
            ) : myGoods.length ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {myGoods.map((g) => (
                  <GoodsCard key={g.id} goods={g} />
                ))}
              </div>
            ) : (
              <EmptyState
                description="还没有发布过商品"
                action={
                  <Button variant="outline" size="sm" onClick={() => navigate('/publish')}>
                    去发布
                  </Button>
                }
              />
            )
          ) : null}

          {/* 收藏 */}
          {activeTab === 'favorites' ? (
            loadingTab.favorites ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-48 w-full rounded-xl" />
                ))}
              </div>
            ) : favorites.length ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {favorites.map((f) => (
                  <GoodsCard key={f.id} goods={f.goods!} />
                ))}
              </div>
            ) : (
              <EmptyState
                description="还没有收藏的商品"
                action={
                  <Button variant="outline" size="sm" onClick={() => navigate('/goods')}>
                    去逛逛
                  </Button>
                }
              />
            )
          ) : null}

          {/* 买到的 / 卖出的 */}
          {activeTab === 'bought' || activeTab === 'sold' ? (
            loadingTab[activeTab] ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 w-full rounded-xl" />
                ))}
              </div>
            ) : orderRows.length ? (
              <>
                <div className="space-y-2">
                  {orderRows.map((o) => (
                    <div
                      key={o.id}
                      onClick={() => navigate(`/orders/${o.id}`)}
                      className="flex cursor-pointer flex-wrap items-center gap-3 rounded-lg border border-border p-3 transition hover:bg-secondary/40"
                    >
                      {o.goodsCoverUrl ? (
                        <img src={o.goodsCoverUrl} alt="" className="size-12 rounded-lg object-cover" />
                      ) : (
                        <div className="grid size-12 place-items-center rounded-lg bg-secondary/60 text-[11px] text-muted-foreground">
                          无图
                        </div>
                      )}
                      <div className="min-w-[140px] flex-1">
                        <div className="text-[13px] font-semibold text-foreground">
                          {o.goodsTitle || '（商品已不可见）'}
                        </div>
                        <div className="mt-0.5 flex items-center gap-1.5 text-[12px] text-muted-foreground">
                          {o.counterpartAvatar ? (
                            <img src={o.counterpartAvatar} alt="" className="size-5 rounded-full object-cover" />
                          ) : (
                            <span className="grid size-5 place-items-center rounded-full bg-secondary text-[10px] text-primary">
                              {(o.counterpartNickname || '同')[0]}
                            </span>
                          )}
                          {o.counterpartNickname}
                        </div>
                      </div>
                      <PriceText value={o.amount} />
                      <Badge variant={ORDER_TONE[o.status] || 'soft'}>{ORDER_TEXT[o.status] || o.status}</Badge>
                      <span className="text-[12px] text-muted-foreground">{shortTime(o.createdAt)}</span>
                      <span className="text-[12px] text-primary">详情</span>
                    </div>
                  ))}
                </div>
                {orderPager(activeTab)}
              </>
            ) : (
              <EmptyState description="暂无订单" />
            )
          ) : null}

          {/* 收到的评价 */}
          {activeTab === 'reviews' ? (
            loadingTab.reviews ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 w-full rounded-xl" />
                ))}
              </div>
            ) : reviewList.length ? (
              <div className="space-y-3">
                {reviewList.map((r) => (
                  <div key={r.id} className="flex gap-3 rounded-lg border border-border p-3">
                    {r.reviewerAvatar ? (
                      <img src={r.reviewerAvatar} alt="" className="size-9 rounded-full object-cover" />
                    ) : (
                      <span className="grid size-9 place-items-center rounded-full bg-secondary text-[13px] text-primary">
                        {(r.reviewerNickname || '同')[0]}
                      </span>
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-semibold text-foreground">{r.reviewerNickname}</span>
                        <span className="inline-flex items-center gap-0.5">
                          {[1, 2, 3, 4, 5].map((n) => (
                            <Star
                              key={n}
                              className={cn(
                                'size-3.5',
                                n <= r.score ? 'fill-[var(--ginger)] text-[var(--ginger)]' : 'text-muted-foreground/40'
                              )}
                            />
                          ))}
                        </span>
                        <span className="ml-auto text-[12px] text-muted-foreground">{shortTime(r.createdAt)}</span>
                      </div>
                      <p className="mt-1 text-[13px] text-muted-foreground">{r.content || '默认好评'}</p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState description="还没有收到评价，完成交易后互评（ORD-06）" />
            )
          ) : null}

          {/* 信用记录 */}
          {activeTab === 'credits' ? (
            loadingTab.credits ? (
              <div className="space-y-3">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-14 w-full rounded-xl" />
                ))}
              </div>
            ) : creditList.length ? (
              <>
                <div className="space-y-2">
                  {creditList.map((c) => (
                    <div
                      key={c.id}
                      className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-2.5"
                    >
                      <div className="min-w-0">
                        <div className="text-[13px] text-foreground">{CREDIT_REASON_TEXT[c.reason] || c.reason}</div>
                        <div className="mt-0.5 text-[12px] text-muted-foreground">{c.createdAt}</div>
                      </div>
                      <div className="shrink-0 text-right">
                        <b
                          className={cn(
                            'text-[15px] tabular-nums',
                            c.scoreChange >= 0 ? 'text-[var(--success)]' : 'text-destructive'
                          )}
                        >
                          {c.scoreChange >= 0 ? '+' : ''}
                          {c.scoreChange}
                        </b>
                        <span className="ml-2 text-[12px] text-muted-foreground">→ {c.afterScore} 分</span>
                      </div>
                    </div>
                  ))}
                </div>
                {creditTotal > 10 ? (
                  <div className="mt-4 flex items-center justify-center gap-3">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={creditPage <= 1}
                      onClick={() => void loadCredits(creditPage - 1)}
                    >
                      上一页
                    </Button>
                    <span className="text-[12px] text-muted-foreground tabular-nums">
                      {creditPage} / {Math.max(1, Math.ceil(creditTotal / 10))}
                    </span>
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={creditPage >= Math.ceil(creditTotal / 10)}
                      onClick={() => void loadCredits(creditPage + 1)}
                    >
                      下一页
                    </Button>
                  </div>
                ) : null}
              </>
            ) : (
              <EmptyState description="暂无信用变动记录，完成交易可加分（PRD §5.7）" />
            )
          ) : null}

          {/* 我的举报 */}
          {activeTab === 'reports' ? (
            <>
              <div className="mb-4 flex overflow-hidden rounded-[9px] border border-border">
                {([
                  { value: '', label: '全部' },
                  { value: 0, label: '待处理' },
                  { value: 1, label: '已处置' },
                  { value: 2, label: '已驳回' }
                ] as const).map((opt) => (
                  <button
                    key={String(opt.value)}
                    type="button"
                    onClick={() => {
                      setReportStatus(opt.value as '' | 0 | 1 | 2)
                      void (async () => {
                        // 与旧版一致：切筛选即回到第 1 页（用最新 status 直接请求）
                        setLoadingTab((prev) => ({ ...prev, reports: true }))
                        try {
                          const res = await fetchReports({
                            ...(opt.value === '' ? {} : { status: opt.value }),
                            pageNum: 1,
                            pageSize: PAGE_SIZE.profileReports
                          }).unwrap()
                          setReportList(res.list ?? [])
                          setReportTotal(res.total ?? 0)
                          setReportPage(1)
                        } finally {
                          setLoadingTab((prev) => ({ ...prev, reports: false }))
                        }
                      })()
                    }}
                    className={cn(
                      'min-h-9 border-r border-border px-3.5 text-[12px] transition last:border-r-0',
                      reportStatus === opt.value
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-card text-muted-foreground hover:bg-secondary/50'
                    )}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>

              {loadingTab.reports ? (
                <div className="space-y-3">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="h-16 w-full rounded-xl" />
                  ))}
                </div>
              ) : reportList.length ? (
                <>
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[760px] text-[13px]">
                      <thead>
                        <tr className="border-b border-border text-left text-[12px] text-muted-foreground">
                          <th className="py-2.5 pr-3 font-medium">举报对象</th>
                          <th className="py-2.5 pr-3 font-medium">举报类型</th>
                          <th className="py-2.5 pr-3 font-medium">状态</th>
                          <th className="py-2.5 pr-3 font-medium">提交时间</th>
                          <th className="py-2.5 font-medium">处置结果</th>
                        </tr>
                      </thead>
                      <tbody>
                        {reportList.map((row) => (
                          <tr key={row.id} className="border-b border-dashed border-border last:border-0">
                            <td className="py-3 pr-3">
                              <Badge variant="outline">{REPORT_TARGET_TEXT[row.targetType] || row.targetType}</Badge>
                              <span className="ml-2 text-foreground">{row.targetTitle || '目标已删除'}</span>
                            </td>
                            <td className="py-3 pr-3 text-foreground">{REPORT_TYPE_TEXT[row.reportType] || row.reportType || '—'}</td>
                            <td className="py-3 pr-3">
                              <Badge variant={row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'info'}>
                                {row.statusText || REPORT_STATUS_TEXT[row.status] || row.status}
                              </Badge>
                            </td>
                            <td className="py-3 pr-3 text-muted-foreground">{row.createdAt || '—'}</td>
                            <td className="py-3">
                              {row.status === 1 || row.status === 2 ? (
                                <>
                                  <div className="text-foreground">{row.result || '—'}</div>
                                  <div className="mt-0.5 text-[12px] text-muted-foreground">
                                    处置于 {row.handledAt || '—'}
                                  </div>
                                </>
                              ) : (
                                <span className="text-muted-foreground">—</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {reportTotal > PAGE_SIZE.profileReports ? (
                    <div className="mt-4 flex items-center justify-center gap-3">
                      <span className="text-[12px] text-muted-foreground">共 {reportTotal} 条</span>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={reportPage <= 1}
                        onClick={() => void loadReports(reportPage - 1)}
                      >
                        上一页
                      </Button>
                      <span className="text-[12px] text-muted-foreground tabular-nums">
                        {reportPage} / {Math.max(1, Math.ceil(reportTotal / PAGE_SIZE.profileReports))}
                      </span>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={reportPage >= Math.ceil(reportTotal / PAGE_SIZE.profileReports)}
                        onClick={() => void loadReports(reportPage + 1)}
                      >
                        下一页
                      </Button>
                    </div>
                  ) : null}
                </>
              ) : (
                <EmptyState description="还没有举报记录，遇到违规内容可在商品 / 求购 / 交换详情页发起举报" />
              )}
            </>
          ) : null}
        </div>
      </Card>

      {/* 编辑资料 */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>编辑资料</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>头像</Label>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => avatarInputRef.current?.click()}
                  className="relative rounded-full transition hover:opacity-90"
                >
                  {editForm.avatar ? (
                    <img src={editForm.avatar} alt="" className="size-14 rounded-full border border-border object-cover" />
                  ) : (
                    <span className="grid size-14 place-items-center rounded-full bg-secondary text-[18px] text-primary">
                      {(editForm.nickname || '我').slice(0, 1)}
                    </span>
                  )}
                  {uploadingAvatar ? (
                    <span className="absolute inset-0 grid place-items-center rounded-full bg-card/70">
                      <Loader2 className="size-4 animate-spin" />
                    </span>
                  ) : null}
                </button>
                <div className="text-[12px] text-muted-foreground">
                  点击更换（jpg/png/webp ≤5MB）
                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept={UPLOAD_ACCEPT}
                    className="hidden"
                    onChange={(e) => void onAvatarPicked(e.target.files)}
                  />
                </div>
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="nickname">昵称</Label>
              <Input
                id="nickname"
                maxLength={50}
                value={editForm.nickname}
                onChange={(e) => setEditForm((prev) => ({ ...prev, nickname: e.target.value }))}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="college">学院</Label>
              <Input
                id="college"
                maxLength={100}
                value={editForm.college}
                onChange={(e) => setEditForm((prev) => ({ ...prev, college: e.target.value }))}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="bio">简介</Label>
              <Textarea
                id="bio"
                rows={3}
                maxLength={200}
                value={editForm.bio}
                onChange={(e) => setEditForm((prev) => ({ ...prev, bio: e.target.value }))}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>
              取消
            </Button>
            <Button disabled={saving} onClick={saveProfile}>
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
