import { useState } from 'react'
import { useRouteNavigate } from '@/components/RouteLink'
import { Clock3, HandCoins, MessageSquare, Plus, Search, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/EmptyState'
import { PriceText } from '@/components/PriceText'
import { Pager } from '@/components/market/Pager'
import { FieldText, Panel, Pill, SectionLabel, Segmented, TrustChip } from '@/components/market/filter-ui'
import { useListCategoriesQuery } from '@/api/goodsApi'
import { usePageWantPostsQuery } from '@/api/wantApi'
import { PAGE_SIZE, WANT_STATUS_TEXT } from '@/lib/constants'
import type { WantPostStatus } from '@/api/types'

/** 状态 Tab 只能选这三态（后端列表接口不接受 DELETED） */
type WantTab = 'OPEN' | 'DEALT' | 'CLOSED'

const STATUS_TABS: { value: WantTab; label: string }[] = [
  { value: 'OPEN', label: '求购中' },
  { value: 'DEALT', label: '已成交' },
  { value: 'CLOSED', label: '已关闭' }
]

const SORT_OPTIONS = [
  { value: 'latest' as const, label: '最新发布' },
  { value: 'budgetDesc' as const, label: '预算高到低' }
]

/**
 * 求购广场（PRD REQ-01/02）：状态 Tab + 分类/关键词/排序筛选 + 单列宽卡 + 分页 + 右下悬浮发布。
 *
 * 视觉：对齐 Stitch 原型「求购广场」——深绿 hero（标语 + 真实求购总数 + 双 CTA）、
 * 药丸分类 + 段控状态/排序、宽卡（左侧标题描述、右侧预算立柱、底部发布者与应约数）。
 *
 * 与 Vue 版对齐（重构视觉时一律未动）：
 * - `pageSize` 固定 **10**；分页器仅在 `total > pageSize` 时渲染
 * - 关键词**只在回车/清空**时触发（无防抖、输入不触发）
 * - 分类单选（一级/二级任一层级都能选，等价旧版 `checkStrictly` 语义）
 * - 卡片右下角显示「我的应约状态」三态 + 应约数 + 相对时间
 */
export default function WantSquarePage() {
  const navigate = useRouteNavigate()
  const [status, setStatus] = useState<WantTab>('OPEN')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [keyword, setKeyword] = useState('')
  const [q, setQ] = useState('')
  const [sort, setSort] = useState<'latest' | 'budgetDesc'>('latest')
  const [pageNum, setPageNum] = useState(1)

  const { data: categories = [] } = useListCategoriesQuery()

  const { data, isFetching } = usePageWantPostsQuery({
    pageNum,
    pageSize: PAGE_SIZE.square,
    status,
    sort,
    ...(categoryId ? { categoryId } : {}),
    ...(q ? { q } : {})
  })

  const posts = data?.list ?? []
  const total = data?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE.square))

  const reload = () => setPageNum(1)

  const shortTime = (t?: string | null) => (t ? String(t).slice(5, 16) : '')

  // 选中二级分类时回查父级，用来渲染「细分」药丸行（仍写同一个 categoryId）
  const activeParent =
    categories.find((c) => c.id === categoryId) ??
    categories.find((c) => (c.children ?? []).some((child) => child.id === categoryId)) ??
    null
  const childCategories = activeParent?.children ?? []

  const pickCategory = (id: number | null) => {
    setCategoryId(id)
    reload()
  }

  return (
    <div className="page relative space-y-4 pb-24">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-2xl bg-primary px-5 py-6 text-primary-foreground shadow-[var(--shadow-lift)] md:px-9 md:py-8">
        <div className="relative z-10 max-w-2xl">
          <p className="text-[11px] font-semibold tracking-[0.16em] text-[#f2d9a8] uppercase">
            CAMPUS WANTED · 校园互助愿望池
          </p>
          <h1 className="mt-2 text-[21px] leading-snug font-extrabold md:text-[32px]">
            找不到心仪的好物？
            <br />
            <span className="text-[#f2d9a8]">发一条求购，让校友送上门！</span>
          </h1>
          <p className="mt-2.5 max-w-xl text-[13px] leading-relaxed text-primary-foreground/80">
            教材笔记、校园代步、应急数码、考研教辅…… 发布精准需求与预算，同校同学面对面自提。
          </p>

          <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px]">
            <span className="inline-flex items-center gap-1.5">
              <ShieldCheck className="size-4 text-[#f2d9a8]" />
              学号实名认证
            </span>
            <span className="inline-flex items-center gap-1.5">
              <HandCoins className="size-4 text-[#f2d9a8]" />
              零手续费
            </span>
            <span className="inline-flex items-center gap-1.5">
              <Clock3 className="size-4 text-[#f2d9a8]" />
              校内当面自提
            </span>
          </div>

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <Button
              onClick={() => navigate('/want/publish')}
              className="bg-primary-foreground text-primary hover:bg-primary-foreground/90"
            >
              <Plus className="size-4" />
              发布求购
            </Button>
            <span className="inline-flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1.5 text-[12px]">
              当前
              <b className="text-[15px] text-[#f2d9a8] tabular-nums">{total}</b>
              条求购
            </span>
          </div>
        </div>

        <div
          aria-hidden
          className="pointer-events-none absolute -top-16 -right-10 size-56 rounded-full bg-white/5"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-24 right-24 size-64 rounded-full bg-white/5"
        />
      </section>

      {/* 筛选 */}
      <Panel className="space-y-4 p-4 lg:p-5">
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <SectionLabel title="期望分类" en="CATEGORY" />
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
              全部求购
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
            <FieldText>状态</FieldText>
            <Segmented
              value={status}
              onChange={(next) => {
                setStatus(next)
                reload()
              }}
              options={STATUS_TABS}
            />
          </div>

          <div className="flex items-center gap-2">
            <FieldText>排序</FieldText>
            <Segmented
              value={sort}
              onChange={(next) => {
                setSort(next)
                reload()
              }}
              options={SORT_OPTIONS}
            />
          </div>

          <div className="flex w-full items-center gap-2 sm:ml-auto sm:w-auto">
            <div className="flex h-9 flex-1 items-center gap-2 rounded-lg border border-input bg-card px-3 sm:flex-none">
              <Search className="size-4 shrink-0 text-muted-foreground" />
              <Input
                value={keyword}
                placeholder="搜索标题或描述"
                className="h-8 w-full border-0 bg-transparent px-0 shadow-none focus-visible:ring-0 sm:w-[190px]"
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setQ(keyword.trim())
                    reload()
                  }
                }}
              />
            </div>
            <Button
              onClick={() => {
                setQ(keyword.trim())
                reload()
              }}
            >
              筛选
            </Button>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
          <TrustChip>校园实名认证</TrustChip>
          <TrustChip>当面交接验货</TrustChip>
          <TrustChip>平台不经手资金</TrustChip>
        </div>
      </Panel>

      {/* 列表 */}
      {isFetching ? (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="space-y-3 rounded-2xl p-5">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-4/5" />
            </Card>
          ))}
        </div>
      ) : posts.length ? (
        <div className="space-y-3">
          {posts.map((post) => (
            <article
              key={post.id}
              onClick={() => navigate(`/want/${post.id}`)}
              role="link"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') navigate(`/want/${post.id}`)
              }}
              className="group cursor-pointer rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-paper)] transition hover:-translate-y-0.5 hover:shadow-[var(--shadow-lift)] focus-visible:ring-2 focus-visible:ring-ring/40 focus-visible:outline-none"
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0 flex-1 space-y-2">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-secondary-foreground">
                      {post.categoryName || '未分类'}
                    </span>
                    {post.status !== 'OPEN' ? (
                      <Badge variant="soft">{WANT_STATUS_TEXT[post.status || ''] || post.status}</Badge>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-[var(--success)]/12 px-2 py-0.5 text-[11px] font-semibold text-[var(--success)]">
                        求购中
                      </span>
                    )}
                    {post.myOfferStatus === 0 ? (
                      <span className="rounded-full bg-[var(--warning)]/15 px-2 py-0.5 text-[11px] font-semibold text-[var(--warning)]">
                        已应约 · 待处理
                      </span>
                    ) : post.myOfferStatus === 1 ? (
                      <span className="rounded-full bg-[var(--success)]/15 px-2 py-0.5 text-[11px] font-semibold text-[var(--success)]">
                        应约已接受
                      </span>
                    ) : post.myOfferStatus === 2 ? (
                      <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                        应约被拒绝
                      </span>
                    ) : null}
                  </div>

                  <h3 className="text-[16px] leading-snug font-bold text-foreground transition-colors group-hover:text-primary">
                    {post.title}
                  </h3>
                  <p className="line-clamp-2 text-[13px] leading-relaxed text-muted-foreground">
                    {post.description}
                  </p>
                </div>

                {/* 预算立柱 */}
                <div className="flex shrink-0 items-baseline justify-between gap-2 rounded-xl bg-secondary/40 px-3 py-2 sm:w-[132px] sm:flex-col sm:items-end sm:bg-transparent sm:px-0 sm:py-0">
                  <span className="text-[11px] text-muted-foreground">求购预算</span>
                  {post.budget !== null && post.budget !== undefined ? (
                    <PriceText value={post.budget} />
                  ) : (
                    <span className="text-[13px] font-semibold text-muted-foreground">价格面议</span>
                  )}
                </div>
              </div>

              <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
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
                </div>

                <div className="flex items-center gap-3 text-[12px] text-muted-foreground">
                  <span className="inline-flex items-center gap-1">
                    <MessageSquare className="size-3.5" />
                    {post.offerCount ?? 0} 人应约
                  </span>
                  <span className="tabular-nums">{shortTime(post.createdAt)}</span>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState
          description="还没有求购帖，发布一条试试"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/want/publish')}>
              发布求购
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
        onClick={() => navigate('/want/publish')}
      >
        <Plus className="size-4" />
        发布求购
      </Button>
    </div>
  )
}
