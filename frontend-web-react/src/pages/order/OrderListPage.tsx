import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import {
  useCancelOrderMutation,
  useCompleteOrderMutation,
  useConfirmOrderMutation,
  usePageOrdersQuery,
  useRejectOrderMutation
} from '@/api/orderApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Pagination } from '@/components/Pagination'
import { EmptyState } from '@/components/EmptyState'
import { PriceText } from '@/components/PriceText'
import { creditLevel, PAGE_SIZE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { OrderListVO, OrderStatus, OrderViewRole } from '@/api/types'

/** 订单状态展示（与 Vue 版 OrderListView/OrderDetailView 的 STATUS_META 逐字一致） */
const STATUS_META: Record<string, { label: string; tone: 'warning' | 'default' | 'success' | 'soft' }> = {
  WAIT_CONFIRM: { label: '待确认', tone: 'warning' },
  SCHEDULED: { label: '待面交', tone: 'default' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'soft' }
}

/** 相对时间（与 Vue 版 relativeTime 逐条一致，含 `replace(/-/g,'/')` 兼容手法） */
function relativeTime(value?: string | null): string {
  if (!value) return ''
  const t = new Date(value.replace(/-/g, '/'))
  const diff = Date.now() - t.getTime()
  if (diff < 60 * 60 * 1000) return `${Math.max(1, Math.floor(diff / 60000))} 分钟前`
  const sameDay = new Date().toDateString() === t.toDateString()
  if (sameDay) return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`
  if (t.getFullYear() === new Date().getFullYear()) return `${t.getMonth() + 1}-${String(t.getDate()).padStart(2, '0')}`
  return value.slice(0, 10)
}

/**
 * 我的订单（PRD ORD-05）：我买到的/我卖出的 Tab + 状态筛选 + 订单行卡 + 操作按钮组。
 *
 * 与 Vue 版对齐：
 * - 操作按钮**完全由后端 `canConfirm/canReject/canCancel/canComplete/canReview` 驱动**，前端不算状态机
 * - `pageSize` 可选 `[20, 40, 60]`（全站唯一带 sizes 的分页）；切 Tab / 改状态都重置到第 1 页
 * - 四种操作的确认文案与 toast 逐字一致；`promptAction` 的 `null` 语义取消、`''` 仍提交
 */
export default function OrderListPage() {
  const navigate = useNavigate()
  const confirm = useConfirm()
  const prompt = usePrompt()

  const [activeTab, setActiveTab] = useState<OrderViewRole>('buyer')
  const [status, setStatus] = useState<'' | OrderStatus>('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState<number>(PAGE_SIZE.orders)

  const { data, isFetching } = usePageOrdersQuery({
    role: activeTab,
    ...(status ? { status } : {}),
    pageNum,
    pageSize
  })

  const [confirmOrder] = useConfirmOrderMutation()
  const [rejectOrder] = useRejectOrderMutation()
  const [cancelOrder] = useCancelOrderMutation()
  const [completeOrder] = useCompleteOrderMutation()

  const orders = data?.list ?? []
  const total = data?.total ?? 0

  const goDetail = (order: OrderListVO) => navigate(`/orders/${order.id}`)

  const onConfirm = async (order: OrderListVO) => {
    const ok = await confirm({
      title: '确认出售',
      description: '确认出售该商品？确认后请与买家约定面交时间。'
    })
    if (!ok) return
    await confirmOrder(order.id)
    toast.success('已确认，订单进入待面交')
  }

  const onReject = async (order: OrderListVO) => {
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

  const onCancel = async (order: OrderListVO) => {
    const ok = await confirm({
      title: '取消订单',
      description: '取消后订单不可恢复，商品将重新上架。',
      confirmText: '确定取消',
      cancelText: '再想想'
    })
    if (!ok) return
    // 旧版列表页 cancel 传空字符串（非 undefined）
    await cancelOrder({ id: order.id, reason: '' })
    toast.success('订单已取消')
  }

  const onComplete = async (order: OrderListVO) => {
    const tip =
      order.type === 'SWAP' ? '请务必当面确认收到物品后再点击确认。' : '请务必当面验收并收到款项后再点击确认。'
    const ok = await confirm({ title: '确认完成交易', description: tip })
    if (!ok) return
    await completeOrder(order.id)
    toast.success('操作成功')
  }

  return (
    <div className="page">
      <h2 className="page-title">我的订单</h2>

      <div className="flex gap-1 border-b border-border">
        {([
          { key: 'buyer', label: '我买到的' },
          { key: 'seller', label: '我卖出的' }
        ] as const).map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => {
              setActiveTab(tab.key)
              setPageNum(1)
            }}
            className={cn(
              'relative px-4 py-2.5 text-[13px] font-semibold transition',
              activeTab === tab.key ? 'text-primary' : 'text-muted-foreground hover:text-primary'
            )}
          >
            {tab.label}
            <span
              className={cn(
                'absolute inset-x-3 -bottom-px h-0.5 rounded-full',
                activeTab === tab.key ? 'bg-primary' : 'bg-transparent'
              )}
            />
          </button>
        ))}
      </div>

      <div className="my-4 flex flex-wrap items-center gap-3">
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as '' | OrderStatus)
            setPageNum(1)
          }}
          className="h-9 rounded-lg border border-input bg-card px-3 text-[13px] text-foreground"
        >
          <option value="">全部状态</option>
          {Object.entries(STATUS_META).map(([key, meta]) => (
            <option key={key} value={key}>
              {meta.label}
            </option>
          ))}
        </select>
        <span className="text-[13px] text-muted-foreground">共 {total} 笔</span>
      </div>

      {isFetching ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="space-y-2 p-4">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-4 w-1/2" />
            </Card>
          ))}
        </div>
      ) : orders.length ? (
        <div className="space-y-3">
          {orders.map((order) => {
            const meta = STATUS_META[order.status] || { label: order.status, tone: 'soft' as const }
            const level = creditLevel(order.counterpartCreditScore)
            return (
              <Card
                key={order.id}
                onClick={() => goDetail(order)}
                className="paper-card flex cursor-pointer flex-wrap items-center gap-4 p-4 transition hover:-translate-y-0.5"
              >
                {order.goodsCoverUrl ? (
                  <img src={order.goodsCoverUrl} alt="" loading="lazy" className="size-[72px] rounded-lg object-cover" />
                ) : (
                  <div className="grid size-[72px] place-items-center rounded-lg bg-secondary/60 text-[12px] text-muted-foreground">
                    无图
                  </div>
                )}

                <div className="min-w-[220px] flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[14px] font-semibold text-foreground">
                      {order.goodsTitle || '（商品已不可见）'}
                    </span>
                    <Badge variant={meta.tone}>{meta.label}</Badge>
                  </div>
                  <div className="mt-1.5 flex flex-wrap items-center gap-3 text-[12px] text-muted-foreground">
                    <span>单号 {order.orderNo}</span>
                    <span>
                      {order.viewRole === 'buyer' ? '卖家' : '买家'}：{order.counterpartNickname || '校园用户'}
                    </span>
                    <Badge variant="outline">信用 {level.text}</Badge>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-3">
                    <PriceText value={order.amount} />
                    <span className="text-[12px] text-muted-foreground">{relativeTime(order.createdAt)}</span>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2" onClick={(e) => e.stopPropagation()}>
                  {order.canConfirm ? (
                    <Button size="sm" onClick={() => onConfirm(order)}>
                      确认出售
                    </Button>
                  ) : null}
                  {order.canReject ? (
                    <Button size="sm" variant="outline" onClick={() => onReject(order)}>
                      拒绝
                    </Button>
                  ) : null}
                  {order.canCancel ? (
                    <Button size="sm" variant="destructive" onClick={() => onCancel(order)}>
                      取消订单
                    </Button>
                  ) : null}
                  {order.canComplete ? (
                    <Button size="sm" onClick={() => onComplete(order)}>
                      {order.type === 'SWAP' ? '确认收货' : '确认收款'}
                    </Button>
                  ) : null}
                  {order.canReview ? (
                    <Button size="sm" variant="accent" onClick={() => goDetail(order)}>
                      去评价
                    </Button>
                  ) : null}
                  <Button size="sm" variant="ghost" onClick={() => goDetail(order)}>
                    查看详情
                  </Button>
                </div>
              </Card>
            )
          })}
        </div>
      ) : (
        <EmptyState
          description="暂无订单，去挑选一件闲置吧"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/goods')}>
              去逛逛
            </Button>
          }
        />
      )}

      {total > 0 ? (
        <Pagination
          pageNum={pageNum}
          pageSize={pageSize}
          total={total}
          pageSizeOptions={[20, 40, 60]}
          onPageChange={(next, size) => {
            setPageNum(next)
            setPageSize(size)
          }}
        />
      ) : null}
    </div>
  )
}
