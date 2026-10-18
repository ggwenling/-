import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Star } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectUserId } from '@/features/auth/authSlice'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import {
  useCancelOrderMutation,
  useCompleteOrderMutation,
  useConfirmOrderMutation,
  useGetOrderQuery,
  useRejectOrderMutation,
  useReviewOrderMutation
} from '@/api/orderApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PriceText } from '@/components/PriceText'
import { creditLevel, CANCELLED_BY_TEXT, ORDER_TYPE_TEXT } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { OrderDetailVO } from '@/api/types'

const STATUS_META: Record<string, { label: string; tone: 'warning' | 'default' | 'success' | 'soft' }> = {
  WAIT_CONFIRM: { label: '待确认', tone: 'warning' },
  SCHEDULED: { label: '待面交', tone: 'default' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'soft' }
}

/** 星级评分（替代 `el-rate`；只读态用于展示双方评价） */
function Rate({
  value,
  onChange,
  readOnly = false
}: {
  value: number
  onChange?: (v: number) => void
  readOnly?: boolean
}) {
  return (
    <span className="inline-flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          disabled={readOnly}
          aria-label={`${n} 星`}
          onClick={() => !readOnly && onChange?.(n)}
          className={cn('p-0.5 transition', readOnly ? 'cursor-default' : 'hover:scale-110')}
        >
          <Star
            className={cn(
              'size-4',
              n <= value ? 'fill-[var(--ginger)] text-[var(--ginger)]' : 'text-muted-foreground/50'
            )}
          />
        </button>
      ))}
    </span>
  )
}

/** 订单进度条（替代 `el-steps`，4 步 + 激活位由状态推导） */
function OrderSteps({
  active,
  steps
}: {
  active: number
  steps: { title: string; description: string }[]
}) {
  return (
    <ol className="mt-4 grid grid-cols-4 gap-2">
      {steps.map((step, idx) => {
        const index = idx + 1
        const done = index < active
        const current = index === active
        return (
          <li key={step.title} className="relative text-center">
            <div className="flex items-center">
              <span className={cn('h-0.5 flex-1', idx === 0 ? 'bg-transparent' : done || current ? 'bg-primary' : 'bg-border')} />
              <span
                className={cn(
                  'grid size-7 shrink-0 place-items-center rounded-full border text-[12px] font-semibold',
                  done || current
                    ? 'border-primary bg-primary text-primary-foreground'
                    : 'border-border bg-card text-muted-foreground'
                )}
              >
                {index}
              </span>
              <span
                className={cn(
                  'h-0.5 flex-1',
                  idx === steps.length - 1 ? 'bg-transparent' : done ? 'bg-primary' : 'bg-border'
                )}
              />
            </div>
            <div className="mt-2 text-[13px] font-semibold text-foreground">{step.title}</div>
            <div className="mt-0.5 text-[11px] break-all text-muted-foreground">{step.description}</div>
          </li>
        )
      })}
    </ol>
  )
}

/**
 * 订单详情（PRD ORD-04~06）：进度时间线（SWAP 双确认单独展示）+ 取消原因 + 操作按钮 + 商品信息 +
 * 交易双方 + 评价区（已评只读 / 可评 / 不可评三态）。
 *
 * 与 Vue 版对齐：`stepActive` 推导规则、`CANCELLED_BY_LABEL`、`TYPE_LABEL`、
 * 评价归属用 `String(id)` 比较、按钮均由后端 `canXxx` 驱动、文案逐字。
 */
export default function OrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const confirm = useConfirm()
  const prompt = usePrompt()
  const myUserId = useAppSelector(selectUserId)

  const orderId = Number(id)
  const { data: order, isLoading } = useGetOrderQuery(orderId, { skip: !Number.isFinite(orderId) || orderId <= 0 })

  const [score, setScore] = useState(0)
  const [content, setContent] = useState('')

  const [confirmOrder] = useConfirmOrderMutation()
  const [rejectOrder] = useRejectOrderMutation()
  const [cancelOrder] = useCancelOrderMutation()
  const [completeOrder] = useCompleteOrderMutation()
  const [reviewOrder, { isLoading: submitting }] = useReviewOrderMutation()

  const onConfirm = async () => {
    if (!order) return
    const ok = await confirm({
      title: '确认出售',
      description: '确认出售该商品？确认后请与买家约定面交时间。'
    })
    if (!ok) return
    await confirmOrder(order.id)
    toast.success('已确认，订单进入待面交')
  }

  const onReject = async () => {
    if (!order) return
    const reason = await prompt({
      title: '拒绝订单',
      description: '拒绝后订单将取消，商品重新上架。可填写拒绝理由：',
      placeholder: '理由（可选，200 字内）',
      confirmText: '拒绝',
      tone: 'destructive'
    })
    if (reason === null) return
    await rejectOrder({ id: order.id, reason: reason || '' })
    toast.success('已拒绝，商品已重新上架')
  }

  const onCancel = async () => {
    if (!order) return
    const ok = await confirm({
      title: '取消订单',
      description: '取消后订单不可恢复，商品将重新上架。',
      confirmText: '确定取消',
      cancelText: '再想想'
    })
    if (!ok) return
    await cancelOrder({ id: order.id, reason: '' })
    toast.success('订单已取消')
  }

  const onComplete = async () => {
    if (!order) return
    const tip =
      order.type === 'SWAP' ? '请务必当面确认收到物品后再点击确认。' : '请务必当面验收并收到款项后再点击确认。'
    const ok = await confirm({ title: '确认完成交易', description: tip })
    if (!ok) return
    await completeOrder(order.id)
    toast.success('操作成功')
  }

  const submitReview = async () => {
    if (!order || !score) return
    try {
      await reviewOrder({ id: order.id, body: { score, content: content || undefined } }).unwrap()
      toast.success('评价成功')
      setScore(0)
      setContent('')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  if (isLoading) {
    return (
      <div className="page grid grid-cols-1 gap-5 lg:grid-cols-[1fr_360px]">
        <Card className="space-y-3 p-5">
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-20 w-full" />
        </Card>
        <Card className="space-y-3 p-5">
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-16 w-full" />
        </Card>
      </div>
    )
  }

  if (!order) {
    return (
      <div className="page">
        <Card className="p-8 text-center">
          <p className="text-muted-foreground">订单不存在或无权查看</p>
          <Button className="mt-4" onClick={() => navigate('/orders')}>
            返回我的订单
          </Button>
        </Card>
      </div>
    )
  }

  const meta = STATUS_META[order.status] || { label: order.status, tone: 'soft' as const }
  const stepActive =
    order.status === 'COMPLETED'
      ? 4
      : order.status === 'SCHEDULED'
        ? 2
        : order.status === 'CANCELLED'
          ? order.confirmedAt
            ? 2
            : 1
          : 1
  const faceTip = order.status === 'COMPLETED' ? (order.completedAt ?? '') : '线下当面验货'
  const steps = [
    { title: '下单', description: order.createdAt },
    {
      title: order.type === 'SWAP' ? '创建即成立' : '卖家确认',
      description: order.confirmedAt || '—'
    },
    { title: '面交', description: faceTip },
    { title: '完成', description: order.completedAt || '—' }
  ]

  const reviews = order.reviews ?? []
  const myReview = reviews.find((r) => String(r.reviewerId) === String(myUserId))
  const counterReview = reviews.find((r) => String(r.reviewerId) !== String(myUserId))
  const reviewPlaceholder =
    order.status !== 'COMPLETED'
      ? '订单完成后（7 天内）双方可互评，允许弃评'
      : '已过评价期（完成后 7 天内有效）或您不是本单参与方'

  const buyerLevel = creditLevel(order.buyer?.creditScore)
  const sellerLevel = creditLevel(order.seller?.creditScore)

  return (
    <div className="page grid grid-cols-1 gap-5 lg:grid-cols-[1fr_360px]">
      {/* 左列 */}
      <div className="space-y-5">
        <Card className="paper-card p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <b className="text-[15px] text-foreground">订单进度</b>
            <Badge variant={meta.tone}>{meta.label}</Badge>
          </div>

          <OrderSteps active={stepActive} steps={steps} />

          {order.type === 'SWAP' ? (
            <div className="mt-4 space-y-2 rounded-lg border border-dashed border-border p-3">
              <div className="flex flex-wrap items-center gap-2 text-[13px]">
                <span className="text-muted-foreground">买方（发起方）确认收货：</span>
                {order.buyerConfirmedAt ? (
                  <Badge variant="success">已确认 {order.buyerConfirmedAt}</Badge>
                ) : (
                  <Badge variant="soft">待确认</Badge>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2 text-[13px]">
                <span className="text-muted-foreground">卖方（帖主）确认收货：</span>
                {order.sellerConfirmedAt ? (
                  <Badge variant="success">已确认 {order.sellerConfirmedAt}</Badge>
                ) : (
                  <Badge variant="soft">待确认</Badge>
                )}
              </div>
            </div>
          ) : null}

          {order.status === 'CANCELLED' ? (
            <div className="mt-4 rounded-lg border border-border bg-secondary/40 px-3 py-2.5 text-[13px]">
              <div className="font-semibold text-foreground">
                订单已取消（{CANCELLED_BY_TEXT[order.cancelledBy || ''] || order.cancelledBy || '未知'}）
              </div>
              <div className="mt-0.5 text-muted-foreground">{order.cancelReason || ''}</div>
            </div>
          ) : null}

          {order.status === 'SCHEDULED' ? (
            <p className="mt-4 text-[13px] leading-relaxed text-muted-foreground">
              面交请在公共场所当面验货，确认无误后再点击确认，保障双方信用（PRD §5.3）。
            </p>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-3">
            {order.canConfirm ? <Button onClick={onConfirm}>确认出售</Button> : null}
            {order.canReject ? (
              <Button variant="outline" onClick={onReject}>
                拒绝
              </Button>
            ) : null}
            {order.canCancel ? (
              <Button variant="destructive" onClick={onCancel}>
                取消订单
              </Button>
            ) : null}
            {order.canComplete ? (
              <Button onClick={onComplete}>{order.type === 'SWAP' ? '确认收货' : '确认收款'}</Button>
            ) : null}
          </div>
        </Card>

        <Card className="paper-card p-5">
          <b className="text-[15px] text-foreground">商品信息</b>
          {order.goodsId ? (
            <div
              className="mt-3 flex cursor-pointer items-center gap-3 rounded-lg p-1 transition hover:bg-secondary/40"
              onClick={() => navigate(`/goods/${order.goodsId}`)}
            >
              {order.goodsCoverUrl ? (
                <img src={order.goodsCoverUrl} alt="" loading="lazy" className="size-[64px] rounded-lg object-cover" />
              ) : (
                <div className="grid size-[64px] place-items-center rounded-lg bg-secondary/60 text-[12px] text-muted-foreground">
                  无图
                </div>
              )}
              <div>
                <div className="text-[14px] font-semibold text-foreground">{order.goodsTitle}</div>
                <PriceText value={order.amount} className="mt-1" />
              </div>
            </div>
          ) : (
            <p className="mt-3 text-[13px] text-muted-foreground">
              该订单类型（{ORDER_TYPE_TEXT[order.type] || order.type}）不关联具体商品
            </p>
          )}
        </Card>
      </div>

      {/* 右列 */}
      <div className="space-y-5">
        <Card className="paper-card p-5">
          <b className="text-[15px] text-foreground">交易双方</b>

          <div className="mt-4 flex items-center gap-3">
            {order.buyer?.avatar ? (
              <img src={order.buyer.avatar} alt="" className="size-10 rounded-full object-cover" />
            ) : (
              <span className="grid size-10 place-items-center rounded-full bg-secondary text-[13px] text-primary">
                {(order.buyer?.nickname || '买')[0]}
              </span>
            )}
            <div>
              <div className="text-[14px] font-semibold text-foreground">{order.buyer?.nickname || '校园用户'}</div>
              <Badge variant="outline" className="mt-1">
                信用 {buyerLevel.text}
              </Badge>
            </div>
          </div>

          <div className="my-3 border-t border-dashed border-border" />

          <div className="flex items-center gap-3">
            {order.seller?.avatar ? (
              <img src={order.seller.avatar} alt="" className="size-10 rounded-full object-cover" />
            ) : (
              <span className="grid size-10 place-items-center rounded-full bg-secondary text-[13px] text-primary">
                {(order.seller?.nickname || '卖')[0]}
              </span>
            )}
            <div>
              <div className="text-[14px] font-semibold text-foreground">{order.seller?.nickname || '校园用户'}</div>
              <Badge variant="outline" className="mt-1">
                信用 {sellerLevel.text}
              </Badge>
            </div>
          </div>

          <div className="my-3 border-t border-dashed border-border" />

          <div className="space-y-2 text-[13px]">
            <div className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">订单类型</span>
              <span className="text-foreground">{ORDER_TYPE_TEXT[order.type] || order.type}</span>
            </div>
            <div className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">订单编号</span>
              <span className="text-foreground">{order.orderNo}</span>
            </div>
            <div className="flex items-center justify-between gap-2">
              <span className="text-muted-foreground">成交金额</span>
              <PriceText value={order.amount} />
            </div>
          </div>
        </Card>

        <Card className="paper-card p-5">
          <b className="text-[15px] text-foreground">交易评价</b>

          {myReview ? (
            <div className="mt-4">
              <div className="flex items-center justify-between gap-2 text-[13px]">
                <span className="text-foreground">我的评价</span>
                <Rate value={myReview.score} readOnly />
              </div>
              <p className="mt-2 text-[13px] text-muted-foreground">{myReview.content || '（未填写内容）'}</p>
            </div>
          ) : order.status === 'COMPLETED' && order.canReview ? (
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between gap-2 text-[13px]">
                <span className="text-foreground">给对方评价</span>
                <Rate value={score} onChange={setScore} />
              </div>
              <Textarea
                rows={3}
                maxLength={200}
                placeholder="说说这次交易体验（可选，200 字内）"
                value={content}
                onChange={(e) => setContent(e.target.value)}
              />
              <Button disabled={!score || submitting} onClick={submitReview}>
                提交评价
              </Button>
            </div>
          ) : (
            <p className="mt-4 text-[13px] leading-relaxed text-muted-foreground">{reviewPlaceholder}</p>
          )}

          {counterReview ? (
            <>
              <div className="my-3 border-t border-dashed border-border" />
              <div className="flex items-center justify-between gap-2 text-[13px]">
                <span className="text-foreground">对方评价</span>
                <Rate value={counterReview.score} readOnly />
              </div>
              <p className="mt-2 text-[13px] text-muted-foreground">{counterReview.content || '（未填写内容）'}</p>
            </>
          ) : null}
        </Card>
      </div>
    </div>
  )
}
