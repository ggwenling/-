import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RouteLink as Link, useRouteNavigate } from '@/components/RouteLink'
import { ChevronRight, RotateCcw, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/EmptyState'
import { GoodsCard } from '@/components/GoodsCard'
import { Pager } from '@/components/market/Pager'
import { FieldText, Panel, Pill, SectionLabel, Segmented, TrustChip } from '@/components/market/filter-ui'
import { useListCategoriesQuery, usePageGoodsQuery } from '@/api/goodsApi'
import { CONDITION_TEXT, PAGE_SIZE } from '@/lib/constants'
import type { GoodsSort } from '@/api/types'

/** 排序项（与 Vue 版 SORT_OPTIONS 逐字一致，顺序也不动） */
const SORT_OPTIONS: { value: GoodsSort; label: string }[] = [
  { value: 'latest', label: '最新发布' },
  { value: 'priceAsc', label: '价格最低' },
  { value: 'priceDesc', label: '价格最高' },
  { value: 'hot', label: '最受欢迎' }
]

/** 预算快捷区间（原型「≤50 / 50-200 / 200-1000 / 1000+」；仍是写进已有的 minPrice/maxPrice） */
const BUDGET_PRESETS: { label: string; min: string; max: string }[] = [
  { label: '≤50 元', min: '', max: '50' },
  { label: '50-200', min: '50', max: '200' },
  { label: '200-1000', min: '200', max: '1000' },
  { label: '1000+', min: '1000', max: '' }
]

/**
 * 商品列表（PRD GDS-03/04/07）：筛选面板（模式/分类/预算/成色/教材字段）+ 排序 + 卡片网格 + 分页。
 *
 * 视觉：对齐 Stitch 原型「商品专区」——面包屑 + 在售计数条 + 横向聚合筛选面板 + 药丸排序 + 四列白卡。
 *
 * ⚠️ **与 Vue 版的一处刻意差异（缺陷修正 #1）**：Vue 版只在 setup 期读一次 `route.query`，
 * 导致"已在 /goods 时再点分类/用顶栏搜索"不会刷新列表（搜索与分类宫格两个入口实际失效）。
 * 这里改为**响应 `route.query` 变化**（`useEffect` 依赖 query），否则演示动线 §1.4/§1.5 无法通过。
 *
 * 保留的旧行为（重构视觉时一律未动）：
 * - 成色只传**单选值**（后端 `conditionLevel` 本就是单值；勾选 ≥2 个时不下发该筛选，与旧版一致）
 * - 分页 20/页、URL 契约 `?categoryId=&q=&pageNum=&tab=`
 * - 页面内**没有**关键词输入框（关键词来自顶栏搜索的 `?q=`）
 */
export default function GoodsListPage() {
  const navigate = useRouteNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  // —— URL → 本地筛选状态（响应 query 变化）——
  const urlCategoryId = Number(searchParams.get('categoryId')) || null
  const urlQ = searchParams.get('q') || ''
  const urlPageNum = Number(searchParams.get('pageNum')) || 1
  const urlTab = searchParams.get('tab') === 'textbook' ? 'textbook' : 'all'

  const [mode, setMode] = useState<'all' | 'textbook'>(urlTab)
  const [categoryId, setCategoryId] = useState<number | null>(urlCategoryId)
  const [q, setQ] = useState(urlQ)
  const [pageNum, setPageNum] = useState(urlPageNum)
  const [sort, setSort] = useState<GoodsSort>('latest')
  const [minPrice, setMinPrice] = useState('')
  const [maxPrice, setMaxPrice] = useState('')
  const [conditionPicked, setConditionPicked] = useState<number[]>([])
  const [courseName, setCourseName] = useState('')
  const [isbn, setIsbn] = useState('')

  useEffect(() => {
    setMode(urlTab)
    setCategoryId(urlCategoryId)
    setQ(urlQ)
    setPageNum(urlPageNum)
  }, [urlTab, urlCategoryId, urlQ, urlPageNum])

  // 用户点击筛选时同步回 URL（保持 URL 契约：?categoryId=&q=&pageNum=&tab=）
  const pushQuery = (next: { categoryId?: number | null; q?: string; pageNum?: number; tab?: 'all' | 'textbook' }) => {
    const params = new URLSearchParams(searchParams)
    const merged = {
      categoryId: next.categoryId !== undefined ? next.categoryId : categoryId,
      q: next.q !== undefined ? next.q : q,
      pageNum: next.pageNum !== undefined ? next.pageNum : pageNum,
      tab: next.tab !== undefined ? next.tab : mode
    }
    if (merged.categoryId) params.set('categoryId', String(merged.categoryId))
    else params.delete('categoryId')
    if (merged.q) params.set('q', merged.q)
    else params.delete('q')
    if (merged.pageNum && merged.pageNum > 1) params.set('pageNum', String(merged.pageNum))
    else params.delete('pageNum')
    if (merged.tab === 'textbook') params.set('tab', 'textbook')
    else params.delete('tab')
    setSearchParams(params, { replace: false })
  }

  // —— 请求参数（成色仅在恰好选中 1 项时下发，与旧版一致）——
  const params = useMemo(
    () => ({
      pageNum,
      pageSize: PAGE_SIZE.goodsList,
      sort,
      ...(categoryId ? { categoryId } : {}),
      ...(conditionPicked.length === 1 ? { conditionLevel: conditionPicked[0] as 1 | 2 | 3 | 4 } : {}),
      ...(minPrice ? { minPrice: Number(minPrice) } : {}),
      ...(maxPrice ? { maxPrice: Number(maxPrice) } : {}),
      ...(q ? { q } : {}),
      ...(mode === 'textbook' && courseName ? { courseName } : {}),
      ...(mode === 'textbook' && isbn ? { isbn } : {})
    }),
    [pageNum, sort, categoryId, conditionPicked, minPrice, maxPrice, q, mode, courseName, isbn]
  )

  const { data: categories = [] } = useListCategoriesQuery()
  const { data, isFetching } = usePageGoodsQuery(params)

  const goodsList = data?.list ?? []
  const total = data?.total ?? 0
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE.goodsList))

  // 选中项若命中二级分类，则回查其父级以渲染「二级细分」药丸行
  const activeParent =
    categories.find((c) => c.id === categoryId) ??
    categories.find((c) => (c.children ?? []).some((child) => child.id === categoryId)) ??
    null
  const childCategories = activeParent?.children ?? []

  const search = () => pushQuery({ pageNum: 1 })

  const reset = () => {
    setConditionPicked([])
    setMinPrice('')
    setMaxPrice('')
    setCourseName('')
    setIsbn('')
    setPageNum(1)
    pushQuery({ categoryId: null, q: '', pageNum: 1 })
  }

  const toggleCondition = (level: number) => {
    setConditionPicked((prev) => (prev.includes(level) ? prev.filter((l) => l !== level) : [...prev, level]))
  }

  const pickCategory = (id: number | null) => {
    setCategoryId(id)
    pushQuery({ categoryId: id, pageNum: 1 })
  }

  const applyBudget = (min: string, max: string) => {
    setMinPrice(min)
    setMaxPrice(max)
    pushQuery({ pageNum: 1 })
  }

  const budgetActive = (min: string, max: string) => minPrice === min && maxPrice === max

  return (
    <div className="page space-y-4">
      {/* 面包屑 + 在售计数（原型的实时指标条） */}
      <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
        <nav aria-label="面包屑" className="flex flex-wrap items-center gap-1.5 text-[12px] text-muted-foreground">
          <Link to="/home" className="transition hover:text-primary">
            首页
          </Link>
          <ChevronRight className="size-3.5 text-muted-foreground/60" />
          <span className="font-semibold text-foreground">全部二手商品</span>
          <span className="mx-1 text-muted-foreground/50">•</span>
          <span className="rounded-full bg-secondary/70 px-2 py-0.5 text-[11px] font-bold text-primary">
            全校流通
          </span>
        </nav>

        <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-3 py-1.5 text-[12px] shadow-xs">
          <span aria-hidden className="size-2 animate-pulse rounded-full bg-primary" />
          <span className="text-muted-foreground">
            在售 <b className="text-primary tabular-nums">{total}</b> 件
          </span>
        </div>
      </div>

      {/* 聚合筛选面板 */}
      <Panel className="space-y-4 p-4 lg:p-5">
        {/* 一级类目 */}
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <SectionLabel title="一级类目" en="CATEGORY" />
            <button
              type="button"
              onClick={() => pickCategory(null)}
              className="inline-flex items-center gap-1 text-[12px] font-medium text-primary transition hover:text-primary/75"
            >
              <RotateCcw className="size-3.5" />
              重置类目
            </button>
          </div>

          <div className="flex flex-wrap gap-2">
            <Pill active={!categoryId} onClick={() => pickCategory(null)}>
              全部好物
            </Pill>
            {categories.map((c) => (
              <Pill key={c.id} active={categoryId === c.id} onClick={() => pickCategory(c.id)}>
                {c.name}
              </Pill>
            ))}
          </div>

          {/* 二级细分：仅当选中项带子分类时出现（仍写同一个 categoryId） */}
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

        {/* 预算 + 成色 */}
        <div className="grid gap-4 border-t border-border pt-4 md:grid-cols-2">
          <div className="space-y-2">
            <FieldText>预算区间（¥）</FieldText>
            <div className="flex flex-wrap items-center gap-2">
              {BUDGET_PRESETS.map((preset) => (
                <Pill
                  key={preset.label}
                  active={budgetActive(preset.min, preset.max)}
                  onClick={() => applyBudget(preset.min, preset.max)}
                >
                  {preset.label}
                </Pill>
              ))}
            </div>
            <div className="flex items-center gap-2">
              <Input
                value={minPrice}
                inputMode="decimal"
                placeholder="最低价"
                onChange={(e) => setMinPrice(e.target.value)}
                className="h-9"
              />
              <span className="text-muted-foreground">—</span>
              <Input
                value={maxPrice}
                inputMode="decimal"
                placeholder="最高价"
                onChange={(e) => setMaxPrice(e.target.value)}
                className="h-9"
              />
            </div>
          </div>

          <div className="space-y-2">
            <FieldText>成色</FieldText>
            <div className="flex flex-wrap items-center gap-2">
              {Object.entries(CONDITION_TEXT).map(([level, text]) => (
                <Pill
                  key={level}
                  active={conditionPicked.includes(Number(level))}
                  onClick={() => toggleCondition(Number(level))}
                >
                  {text}
                </Pill>
              ))}
            </div>
            <p className="text-[11px] leading-relaxed text-muted-foreground">
              成色为单选筛选：只勾 1 项时下发生效，多选或全不选表示不限。
            </p>
          </div>
        </div>

        {/* 教材专区 + 应用/重置 */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
          <div className="flex flex-wrap items-center gap-3">
            <Segmented
              value={mode}
              onChange={(next) => {
                setMode(next)
                setCourseName('')
                setIsbn('')
                pushQuery({ tab: next, pageNum: 1 })
              }}
              options={[
                { value: 'all', label: '全部商品' },
                { value: 'textbook', label: '教材专区' }
              ]}
            />
            {mode === 'textbook' ? (
              <>
                <Input
                  value={courseName}
                  placeholder="课程名，如：高等数学"
                  onChange={(e) => setCourseName(e.target.value)}
                  className="h-9 w-[170px]"
                />
                <Input
                  value={isbn}
                  placeholder="ISBN"
                  onChange={(e) => setIsbn(e.target.value)}
                  className="h-9 w-[130px]"
                />
              </>
            ) : null}
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={search}>应用筛选</Button>
            <Button variant="ghost" onClick={reset}>
              重置
            </Button>
          </div>
        </div>

        {/* 静态交易保障标（原型同位置） */}
        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
          <ShieldCheck className="size-3.5 text-primary" />
          <TrustChip>校园实名认证</TrustChip>
          <TrustChip>线下当面验货</TrustChip>
          <TrustChip>平台不经手资金</TrustChip>
        </div>
      </Panel>

      {/* 排序 + 计数 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <FieldText>排序</FieldText>
          <Segmented
            value={sort}
            onChange={(next) => {
              setSort(next)
              setPageNum(1)
            }}
            options={SORT_OPTIONS}
          />
        </div>
        <span className="text-[12px] text-muted-foreground">
          共 <b className="text-foreground tabular-nums">{total}</b> 件
        </span>
      </div>

      {/* 列表 */}
      {isFetching ? (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="space-y-3 rounded-2xl border border-border bg-card p-2 pb-3">
              <Skeleton className="aspect-[4/3] w-full rounded-xl" />
              <Skeleton className="h-4 w-4/5" />
              <Skeleton className="h-4 w-3/5" />
            </div>
          ))}
        </div>
      ) : goodsList.length ? (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
          {goodsList.map((g) => (
            <GoodsCard key={g.id} goods={g} variant="plain" />
          ))}
        </div>
      ) : (
        <EmptyState
          description="没有符合条件的商品"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/publish')}>
              去发布一件
            </Button>
          }
        />
      )}

      <Pager
        className="pt-2"
        page={pageNum}
        pageCount={pageCount}
        total={total}
        unit="件"
        onChange={(next) => {
          setPageNum(next)
          pushQuery({ pageNum: next })
        }}
      />
    </div>
  )
}
