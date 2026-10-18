import { useState } from 'react'
import { useRouteNavigate } from '@/components/RouteLink'
import { ArrowLeftRight, HandCoins, MessageSquare, Package, Plus, Search, ShieldCheck, Sparkles, Star } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/EmptyState'
import { Pager } from '@/components/market/Pager'
import { FieldText, Panel, Pill, SectionLabel, Segmented, TrustChip } from '@/components/market/filter-ui'
import { useListCategoriesQuery } from '@/api/goodsApi'
import { usePageSwapPostsQuery } from '@/api/swapApi'
import { PAGE_SIZE, SWAP_STATUS_TEXT } from '@/lib/constants'
import type { SwapPostCardVO, SwapPostStatus } from '@/api/types'

/** 状态 Tab 只能选这三态（后端列表接口不接受 DELETED） */
type SwapTab = 'OPEN' | 'DEALT' | 'CLOSED'

const STATUS_TABS: { value: SwapTab; label: string }[] = [
  { value: 'OPEN', label: '交换中' },
  { value: 'DEALT', label: '已成交' },
  { value: 'CLOSED', label: '已关闭' }
]

/** 补差价金额（>0 才展示，与旧版 `hasDiffAmount` 一致） */
const hasDiffAmount = (post: SwapPostCardVO) =>
  post.diffAmount !== null && post.diffAmount !== undefined && Number(post.diffAmount) > 0

/**
 * 交换广场（PRD SWP-01/02）：状态 Tab + 分类/关键词筛选 +「物品对照」宽卡 + 分页 + 悬浮发布。
 *
 * 视觉：对齐 Stitch 原型「以物交换」——深绿 hero（零现金主张 + 真实帖数 + CTA）、
 * 药丸分类 + 段控状态、卡片内「我提供 ↔ 我想换」对照块 + 发布者/补差价/待处理数脚注。
 *
 * 与 Vue 版对齐（重构视觉时一律未动）：`pageSize` 固定 **10**、无排序控件、
 * 关键词回车/清空触发、卡片显示「可补差价 ¥x / 不补差价」与「已发起 · 待处理 / 已达成交换」。
 */
export default function SwapSquarePage() {
  const navigate = useRouteNavigate()
  const [status, setStatus] = useState<SwapTab>('OPEN')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const [q, setQ] = useState('')
  const [pageNum, setPageNum] = useState(1)

  const { data: categories = [] } = useListCategoriesQuery()

  const { data, isFetching } = usePageSwapPostsQuery({
    pageNum,
    pageSize: PAGE_SIZE.square,
    status,
    ...(categoryId ? { categoryId } : {}),
    ...(q ? { q } : {})
  })

  const posts = data?.list ?? []
  const total = data?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE.square))

  const activeParent =
    categories.find((c) => c.id === categoryId) ??
    categories.find((c) => (c.children ?? []).some((child) => child.id === categoryId)) ??
    null
  const childCategories = activeParent?.children ?? []

  const pickCategory = (id: number | null) => {
    setCategoryId(id)
    setPageNum(1)
  }

  return (
    <div className="page relative space-y-4 pb-24">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-2xl bg-primary px-5 py-6 text-primary-foreground shadow-[var(--shadow-lift)] md:px-9 md:py-8">
        <div className="relative z-10 max-w-2xl">
          <p className="text-[11px] font-semibold tracking-[0.16em] text-[#f2d9a8] uppercase">
            ZERO CASH · CAMPUS ECO SWAP
          </p>
          <h1 className="mt-2 text-2xl leading-tight font-extrabold md:text-[32px]">
            以物易物，各取所需
            <br />
            <span className="text-[#f2d9a8]">零金钱成本的校园互换</span>
          </h1>
          <p className="mt-2.5 max-w-xl text-[13px] leading-relaxed text-primary-foreground/80">
            手头闲置换你的心头好：数码对换、书刊换读、兴趣装备互换。用一次校内互换，让闲置继续流动。
          </p>

          <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px]">
            <span className="inline-flex items-center gap-1.5">
              <ShieldCheck className="size-4 text-[#f2d9a8]" />
              学号实名认证
            </span>
            <span className="inline-flex items-center gap-1.5">
              <HandCoins className="size-4 text-[#f2d9a8]" />
              零现金消耗
            </span>
            <span className="inline-flex items-center gap-1.5">
              <ArrowLeftRight className="size-4 text-[#f2d9a8]" />
              当面验货
            </span>
          </div>

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <Button
              onClick={() => navigate('/swap/publish')}
              className="bg-primary-foreground text-primary hover:bg-primary-foreground/90"
            >
              <Plus className="size-4" />
              发起一次物品交换
            </Button>
            <span className="inline-flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1.5 text-[12px]">
              当前
              <b className="text-[15px] text-[#f2d9a8] tabular-nums">{total}</b>
              个交换帖
            </span>
          </div>
        </div>

        <div aria-hidden className="pointer-events-none absolute -top-16 -right-10 size-56 rounded-full bg-white/5" />
        <div aria-hidden className="pointer-events-none absolute -bottom-24 right-24 size-64 rounded-full bg-white/5" />
      </section>

      {/* 筛选 */}
      <Panel className="space-y-4 p-4 lg:p-5">
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <SectionLabel title="物品分类" en="CATEGORY" />
            <button
              type="button"
              onClick={() => pickCategory(null)}
              className="text-[12px] font-medium text-primary transition hover:text-primary/75"
            >
              重置分类
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            <Pill active={!categoryId} onClick={() => pickCategory(null)}>
              全部交换
            </Pill>
            {categories.map((c) => (
              <Pill key={c.id} active={categoryId === c.id} onClick={() => pickCategory(c.id)}>
                {c.name}
              </Pill>
            ))}
          </div>
          {childCategories.length ? (
            <div className="flex flex-wrap items-center gap-2 rounded-xl bg-secondary/30 px-3 py-2">
              <span className="text-[11px] text-muted-foreground">细分</span>
              {childCategories.map((child) => (
                <Pill key={child.id} active={categoryId === child.id} onClick={() => pickCategory(child.id)}>
                  {child.name}
                </Pill>
              ))}
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
          <div className="flex items-center gap-2">
            <FieldText>交换状态</FieldText>
            <Segmented
              value={status}
              onChange={(next) => {
                setStatus(next)
                setPageNum(1)
              }}
              options={STATUS_TABS}
            />
          </div>

          <div className="flex w-full items-center gap-2 sm:ml-auto sm:w-auto">
            <div className="flex h-9 flex-1 items-center gap-2 rounded-lg border border-input bg-card px-3 sm:flex-none">
              <Search className="size-4 shrink-0 text-muted-foreground" />
              <Input
                value={keyword}
                placeholder="搜索标题或物品描述"
                className="h-8 w-full border-0 bg-transparent px-0 shadow-none focus-visible:ring-0 sm:w-[200px]"
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setQ(keyword.trim())
                    setPageNum(1)
                  }
                }}
              />
            </div>
            <Button
              onClick={() => {
                setQ(keyword.trim())
                setPageNum(1)
              }}
            >
              筛选
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
          <TrustChip>校园实名认证</TrustChip>
          <TrustChip>当面自提验货</TrustChip>
          <TrustChip>零现金消耗</TrustChip>
        </div>
      </Panel>

      {/* 列表 */}
      {isFetching ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="space-y-3 rounded-2xl p-5">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-16 w-full" />
            </Card>
          ))}
        </div>
      ) : posts.length ? (
        <div className="space-y-3">
          {posts.map((post) => (
            <article
              key={post.id}
              onClick={() => navigate(`/swap/${post.id}`)}
              role="link"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') navigate(`/swap/${post.id}`)
              }}
              className="group cursor-pointer rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-paper)] transition hover:-translate-y-0.5 hover:shadow-[var(--shadow-lift)] focus-visible:ring-2 focus-visible:ring-ring/40 focus-visible:outline-none"
            >
              {/* 头：发布者 + 状态 */}
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex min-w-0 items-center gap-2">
                  {post.publisher?.avatar ? (
                    <img src={post.publisher.avatar} alt="" className="size-7 rounded-full object-cover" />
                  ) : (
                    <span className="grid size-7 place-items-center rounded-full bg-secondary text-[11px] font-semibold text-primary">
                      {(post.publisher?.nickname || '同')[0]}
                    </span>
                  )}
                  <span className="truncate text-[13px] font-medium text-foreground">
                    {post.publisher?.nickname || '校园用户'}
                  </span>
                  <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] text-secondary-foreground">
                    {post.categoryName || '未分类'}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {post.status !== 'OPEN' ? (
                    <Badge variant="soft">{SWAP_STATUS_TEXT[post.status || ''] || post.status}</Badge>
                  ) : (
                    <span className="inline-flex items-center gap-1 rounded-full bg-[var(--success)]/12 px-2 py-0.5 text-[11px] font-semibold text-[var(--success)]">
                      <Sparkles className="size-3" />
                      开放交换中
                    </span>
                  )}
                </div>
              </div>

              <h3 className="mt-3 text-[16px] leading-snug font-bold text-foreground transition-colors group-hover:text-primary">
                {post.title}
              </h3>

              {/* 物品对照：我提供 ↔ 我想换 */}
              <div className="mt-3 grid gap-2 sm:grid-cols-[1fr_auto_1fr] sm:items-stretch">
                <div className="rounded-xl border border-border bg-secondary/30 p-3">
                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-primary">
                    <Package className="size-3.5" />
                    我提供
                  </span>
                  <p className="mt-1 line-clamp-3 text-[13px] leading-relaxed text-foreground">{post.myItemDesc}</p>
                </div>

                <div className="hidden place-items-center text-muted-foreground sm:grid">
                  <ArrowLeftRight className="size-4" />
                </div>

                <div className="rounded-xl border border-border bg-[var(--ginger)]/8 p-3">
                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--stamp)]">
                    <Star className="size-3.5" />
                    我想换取
                  </span>
                  <p className="mt-1 line-clamp-3 text-[13px] leading-relaxed text-foreground">{post.wantItemDesc}</p>
                </div>
              </div>

              {/* 脚注：补差价 / 我的请求状态 / 待处理数 */}
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
                <div className="flex flex-wrap items-center gap-2 text-[12px]">
                  {post.allowDiff === 1 ? (
                    <Badge variant="warning">
                      可补差价{hasDiffAmount(post) ? ` ¥${Number(post.diffAmount).toFixed(2)}` : ''}
                    </Badge>
                  ) : (
                    <Badge variant="outline">不补差价</Badge>
                  )}
                  {post.myRequestStatus === 0 ? (
                    <span className="rounded-full bg-[var(--warning)]/15 px-2 py-0.5 text-[11px] font-semibold text-[var(--warning)]">
                      已发起 · 待处理
                    </span>
                  ) : post.myRequestStatus === 1 ? (
                    <span className="rounded-full bg-[var(--success)]/15 px-2 py-0.5 text-[11px] font-semibold text-[var(--success)]">
                      已达成交换
                    </span>
                  ) : null}
                </div>

                <span className="inline-flex items-center gap-1 text-[12px] text-muted-foreground">
                  <MessageSquare className="size-3.5" />
                  {post.pendingRequestCount ?? 0} 人想换
                </span>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState
          description="还没有交换帖，发布一条试试"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/swap/publish')}>
              发布交换
            </Button>
          }
        />
      )}

      {total > PAGE_SIZE.square ? (
        <Pager
          page={pageNum}
          pageCount={pageCount}
          total={total}
          unit="条"
          onChange={(next) => setPageNum(next)}
        />
      ) : null}

      <Button
        size="lg"
        className="fixed right-8 bottom-12 z-10 rounded-full shadow-lg"
        onClick={() => navigate('/swap/publish')}
      >
        <Plus className="size-4" />
        发布交换
      </Button>
    </div>
  )
}
