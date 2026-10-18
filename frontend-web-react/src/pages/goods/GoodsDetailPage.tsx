import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { RouteLink as Link, useRouteNavigate } from '@/components/RouteLink'
import {
  Bookmark,
  ChevronLeft,
  ChevronRight,
  Eye,
  MapPin,
  MessageCircle,
  Star,
  TriangleAlert
} from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn, selectUserId } from '@/features/auth/authSlice'
import {
  useAddFavoriteMutation,
  useDeleteGoodsMutation,
  useGetGoodsQuery,
  useOffSaleGoodsMutation,
  useOnSaleGoodsMutation,
  usePageGoodsQuery,
  useRemoveFavoriteMutation,
  useRestoreGoodsMutation
} from '@/api/goodsApi'
import { useCreateOrderMutation } from '@/api/orderApi'
import { useSendMessageMutation } from '@/api/messageApi'
import { useConfirm } from '@/components/confirm'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/EmptyState'
import { GoodsCard } from '@/components/GoodsCard'
import { PriceText } from '@/components/PriceText'
import { UserCard } from '@/components/UserCard'
import { ReportDialog } from '@/components/ReportDialog'
import { CONDITION_TEXT, MSG_TYPE } from '@/lib/constants'
import type { ReportTargetType } from '@/api/types'

/** 非在售状态下的「想要」按钮提示（PRD §5.3 商品状态机，文案与旧版逐字一致） */
const STATUS_TIP: Record<string, string> = {
  IN_TRANSACTION: '该商品已有买家下单，交易进行中',
  SOLD: '该商品已售出',
  OFF_SALE: '该商品已下架',
  DELETED: '该商品已删除'
}

/**
 * 商品详情（PRD GDS-05/06/08）：图集 + 信息 + 收藏 + 卖家卡 + 相似推荐 + 举报入口 + 卖家操作。
 *
 * 与 Vue 版对齐的关键行为：
 * - 请求按 `id` 缓存/切换（等价于旧版 `loadSeq` 竞态守卫：旧 id 的响应不会覆盖新 id 的渲染）
 * - 相似推荐优先用详情内 `similarGoods`（过滤自身取 6），为空且同分类时回退热门列表
 * - 未登录点「想要/收藏/聊一聊」→ 跳登录并带 `redirect`；点举报额外先提示「请先登录」
 * - 收藏为**乐观本地更新**（`favorited` 取反 + `favoriteCount` ±1）
 * - 卖家操作：下架/删除有二次确认，上架/恢复无确认；文案逐字一致
 */
export default function GoodsDetailPage() {
  const { id } = useParams()
  const navigate = useRouteNavigate()
  const confirm = useConfirm()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const myUserId = useAppSelector(selectUserId)

  const goodsId = Number(id)
  const skip = !Number.isFinite(goodsId) || goodsId <= 0

  const { data: detail, isLoading, isError } = useGetGoodsQuery(goodsId, { skip })

  const [favOverride, setFavOverride] = useState<{ favorited: boolean; count: number } | null>(null)
  const [imgIndex, setImgIndex] = useState(0)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportType, setReportType] = useState<ReportTargetType>('GOODS')

  const [addFavorite] = useAddFavoriteMutation()
  const [removeFavorite] = useRemoveFavoriteMutation()
  const [offSaleGoods] = useOffSaleGoodsMutation()
  const [onSaleGoods] = useOnSaleGoodsMutation()
  const [restoreGoods] = useRestoreGoodsMutation()
  const [deleteGoods] = useDeleteGoodsMutation()
  const [createOrder] = useCreateOrderMutation()
  const [sendMessage] = useSendMessageMutation()

  // 详情换 id 时重置本地覆盖与图集下标（等价旧版 watch route.params.id 重新 load）
  useEffect(() => {
    setFavOverride(null)
    setImgIndex(0)
  }, [goodsId])

  const images = detail?.images ?? []
  const favorited = favOverride ? favOverride.favorited : detail?.favorited === true
  const favoriteCount = favOverride ? favOverride.count : (detail?.favoriteCount ?? 0)
  const isOwner = !!(detail && myUserId && detail.seller?.id === myUserId)
  const conditionText = CONDITION_TEXT[Number(detail?.conditionLevel)] || '—'
  const statusTip = STATUS_TIP[detail?.status || ''] || '当前不可下单'
  const wantLabel = detail?.status === 'SOLD' ? '已售出' : '想要'

  // 相似推荐：详情内字段优先，为空则回退同分类热门
  const fromDetail = (detail?.similarGoods ?? []).filter((g) => g.id !== detail?.id).slice(0, 6)
  const needFallback = !!detail && fromDetail.length === 0 && !!detail.categoryId
  const { data: fallback } = usePageGoodsQuery(
    { categoryId: detail?.categoryId, pageNum: 1, pageSize: 7, sort: 'hot' },
    { skip: !needFallback }
  )
  const similar = fromDetail.length
    ? fromDetail
    : (fallback?.list ?? []).filter((g) => g.id !== detail?.id).slice(0, 6)

  const requireLogin = () => {
    if (isLoggedIn) return false
    navigate(`/login?redirect=${encodeURIComponent(`/goods/${goodsId}`)}`)
    return true
  }

  const handleWant = async () => {
    if (requireLogin()) return
    try {
      const created = await createOrder(goodsId).unwrap()
      toast.success('下单成功！请在聊天中与卖家约好面交时间地点')
      // POST /orders 返回整个 OrderDetailVO，新单 id 取 created.id（与 Vue 版 res.data.id 一致）
      navigate(`/orders/${created.id}`)
    } catch (err) {
      const msg = (err as Error)?.message || ''
      if (msg.includes('认证')) navigate('/verify')
    }
  }

  const handleChat = async () => {
    if (requireLogin()) return
    if (!detail?.seller?.id) return
    try {
      await sendMessage({
        peerUserId: detail.seller.id,
        msgType: MSG_TYPE.GOODS_CARD,
        content: String(detail.id)
      }).unwrap()
      toast.success('已发送商品卡片')
      navigate('/message')
    } catch {
      /* 敏感词/限频等错误由 baseQuery 统一提示 */
    }
  }

  const toggleFavorite = async () => {
    if (requireLogin()) return
    if (!detail) return
    if (favorited) {
      await removeFavorite(detail.id)
      setFavOverride({ favorited: false, count: Math.max(0, favoriteCount - 1) })
      toast.success('已取消收藏')
    } else {
      await addFavorite(detail.id)
      setFavOverride({ favorited: true, count: favoriteCount + 1 })
      toast.success('已加入收藏')
    }
  }

  const doOffSale = async () => {
    const ok = await confirm({
      title: '下架商品',
      description: '下架后商品将不在列表展示，可随时重新上架',
      confirmText: '确定'
    })
    if (!ok || !detail) return
    await offSaleGoods(detail.id)
    toast.success('已下架')
  }

  const doOnSale = async () => {
    if (!detail) return
    await onSaleGoods(detail.id)
    toast.success('已重新上架')
  }

  const doRestore = async () => {
    if (!detail) return
    await restoreGoods(detail.id)
    toast.success('已恢复，确认无误后可点击「重新上架」')
  }

  const doDelete = async () => {
    const ok = await confirm({
      title: '删除商品',
      description: '删除后商品不再公开展示，30 天内可自行恢复',
      confirmText: '删除',
      tone: 'destructive'
    })
    if (!ok || !detail) return
    await deleteGoods(detail.id)
    toast.success('已删除')
    // 旧版跳 `/`（当时即商城主页）；`/` 现为落地页 → 映射到 `/home`
    navigate('/home')
  }

  const openReport = (type: ReportTargetType) => {
    if (!isLoggedIn) {
      toast.warning('请先登录')
      navigate(`/login?redirect=${encodeURIComponent(`/goods/${goodsId}`)}`)
      return
    }
    setReportType(type)
    setReportOpen(true)
  }

  if (isLoading) {
    return (
      <div className="page">
        <Card className="my-6 space-y-4 p-6">
          <Skeleton className="h-[300px] w-full rounded-xl" />
          <Skeleton className="h-6 w-2/3" />
          <Skeleton className="h-5 w-1/3" />
        </Card>
      </div>
    )
  }

  if (isError || !detail) {
    return (
      <div className="page">
        <EmptyState
          description="商品不存在或已删除"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/')}>
              回首页看看
            </Button>
          }
        />
      </div>
    )
  }

  const reportTarget =
    reportType === 'USER'
      ? { id: detail.seller?.id ?? null, title: detail.seller?.nickname || '' }
      : { id: detail.id, title: detail.title || '' }

  return (
    <div className="page">
      <div className="grid grid-cols-1 gap-6 pt-3 pb-7 lg:grid-cols-[1fr_400px]">
        {/* 左：图集 */}
        <div className="paper-card overflow-hidden">
          {images.length ? (
            <div className="relative">
              <img
                src={images[Math.min(imgIndex, images.length - 1)]?.url}
                alt=""
                className="block h-[420px] w-full object-cover"
              />
              {images.length > 1 ? (
                <>
                  <button
                    type="button"
                    aria-label="上一张"
                    onClick={() => setImgIndex((i) => (i - 1 + images.length) % images.length)}
                    className="absolute top-1/2 left-3 grid size-9 -translate-y-1/2 place-items-center rounded-full bg-card/85 text-foreground shadow-sm transition hover:bg-card"
                  >
                    <ChevronLeft className="size-4" />
                  </button>
                  <button
                    type="button"
                    aria-label="下一张"
                    onClick={() => setImgIndex((i) => (i + 1) % images.length)}
                    className="absolute top-1/2 right-3 grid size-9 -translate-y-1/2 place-items-center rounded-full bg-card/85 text-foreground shadow-sm transition hover:bg-card"
                  >
                    <ChevronRight className="size-4" />
                  </button>
                  <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-1.5">
                    {images.map((img, i) => (
                      <button
                        key={img.url}
                        type="button"
                        aria-label={`第 ${i + 1} 张`}
                        onClick={() => setImgIndex(i)}
                        className={
                          i === imgIndex
                            ? 'size-2.5 rounded-full bg-primary'
                            : 'size-2.5 rounded-full bg-card/80 ring-1 ring-border'
                        }
                      />
                    ))}
                  </div>
                </>
              ) : null}
            </div>
          ) : (
            <div className="flex h-[420px] items-center justify-center text-muted-foreground">暂无图片</div>
          )}
        </div>

        {/* 右：信息 + 操作 + 卖家卡 */}
        <div className="flex flex-col gap-3.5">
          <h1 className="m-0 text-[22px] leading-relaxed font-bold text-foreground">{detail.title}</h1>

          <div className="paper-card px-4 py-3">
            <PriceText value={detail.price} size="large" />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="soft">{conditionText}</Badge>
            <Badge variant="outline">
              {detail.parentCategoryName ? `${detail.parentCategoryName} / ${detail.categoryName}` : detail.categoryName}
            </Badge>
            {(detail.tags ?? []).map((t) => (
              <Badge key={t.id} variant="accent">
                {t.name}
              </Badge>
            ))}
          </div>

          <div className="flex flex-wrap items-center gap-3 text-[13px] text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Eye className="size-3.5" /> 浏览 {detail.viewCount}
            </span>
            <span className="inline-flex items-center gap-1">
              <Star className="size-3.5" /> 想要 {detail.wantCount}
            </span>
            <span className="inline-flex items-center gap-1">
              <Bookmark className="size-3.5" /> 收藏 {favoriteCount}
            </span>
            {detail.tradeLocation ? (
              <span className="ml-auto inline-flex items-center gap-1">
                <MapPin className="size-3.5" /> {detail.tradeLocation}
              </span>
            ) : null}
          </div>

          {detail.courseName || detail.isbn ? (
            <div className="flex flex-wrap items-center gap-2">
              {detail.courseName ? <Badge variant="warning">课程：{detail.courseName}</Badge> : null}
              {detail.isbn ? <Badge variant="warning">ISBN：{detail.isbn}</Badge> : null}
            </div>
          ) : null}

          <div className="mt-1 flex flex-wrap gap-3">
            <Button size="lg" onClick={handleChat}>
              <MessageCircle className="size-4" />
              聊一聊
            </Button>
            {detail.status !== 'ON_SALE' ? (
              <Button size="lg" variant="outline" disabled title={statusTip}>
                <Star className="size-4" />
                {wantLabel}
              </Button>
            ) : (
              <Button size="lg" variant="accent" onClick={handleWant}>
                <Star className="size-4" />
                想要
              </Button>
            )}
            <Button size="lg" variant={favorited ? 'destructive' : 'outline'} onClick={toggleFavorite}>
              <Bookmark className="size-4" />
              {favorited ? '已收藏' : '收藏'}
            </Button>
          </div>

          {!isOwner ? (
            <button
              type="button"
              onClick={() => openReport('GOODS')}
              className="inline-flex w-fit items-center gap-1.5 text-[13px] text-muted-foreground transition hover:text-destructive"
            >
              <TriangleAlert className="size-3.5" />
              举报商品
            </button>
          ) : null}

          {detail.seller ? <UserCard user={detail.seller} /> : null}

          {!isOwner && detail.seller ? (
            <button
              type="button"
              onClick={() => openReport('USER')}
              className="inline-flex w-fit items-center gap-1.5 text-[13px] text-muted-foreground transition hover:text-destructive"
            >
              <TriangleAlert className="size-3.5" />
              举报该用户
            </button>
          ) : null}

          {isOwner ? (
            <div className="flex flex-wrap items-center gap-2.5">
              <Badge variant="soft">这是你发布的商品</Badge>
              <Button size="sm" variant="outline" asChild>
                <Link to={`/publish?id=${detail.id}`}>编辑</Link>
              </Button>
              {detail.status === 'ON_SALE' ? (
                <Button size="sm" variant="outline" onClick={doOffSale}>
                  下架
                </Button>
              ) : null}
              {detail.status === 'OFF_SALE' ? (
                <Button size="sm" variant="outline" onClick={doOnSale}>
                  重新上架
                </Button>
              ) : null}
              {detail.status === 'DELETED' ? (
                <Button size="sm" variant="outline" onClick={doRestore}>
                  恢复（30 天内）
                </Button>
              ) : null}
              <Button size="sm" variant="destructive" onClick={doDelete}>
                删除
              </Button>
            </div>
          ) : null}
        </div>
      </div>

      {/* 商品描述 */}
      <Card className="paper-card mb-6 p-5">
        <b className="text-[15px] text-foreground">商品描述</b>
        <div className="mt-3 leading-[1.8] whitespace-pre-wrap text-foreground">{detail.description}</div>
      </Card>

      {/* 相似推荐 */}
      <h2 className="mb-4 text-xl font-bold text-foreground">相似推荐</h2>
      {similar.length ? (
        <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
          {similar.map((g) => (
            <GoodsCard key={g.id} goods={g} />
          ))}
        </div>
      ) : (
        <EmptyState description="暂无相似商品" />
      )}

      <ReportDialog
        open={reportOpen}
        onOpenChange={setReportOpen}
        targetType={reportType}
        targetId={reportTarget.id}
        targetTitle={reportTarget.title}
      />
    </div>
  )
}
