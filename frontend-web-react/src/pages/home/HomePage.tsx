import { useState } from 'react'
import { useRouteNavigate } from '@/components/RouteLink'
import {
  ArrowRight,
  Clock3,
  Flame,
  HandCoins,
  MapPin,
  Megaphone,
  RefreshCw,
  Search,
  ShieldCheck,
  ShieldHalf,
  TrendingUp
} from 'lucide-react'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn } from '@/features/auth/authSlice'
import { useListCategoriesQuery, usePageGoodsQuery } from '@/api/goodsApi'
import { useRecommendHomeQuery, useRecommendHotQuery } from '@/api/recommendApi'
import { GoodsCard } from '@/components/GoodsCard'
import { HomeHeroCarousel } from '@/components/HomeHeroCarousel'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PAGE_SIZE } from '@/lib/constants'

const KICKER = 'text-[11px] font-semibold tracking-[0.14em] text-muted-foreground uppercase'

/**
 * 首页分类图标的色块与 emoji。
 * 分类是后台可增删的字典（种子 10 个），因此**先按名称关键词匹配、再按序号兜底**，
 * 避免运营调整顺序后图标整体错位。
 */
const CATEGORY_STYLES: { emoji: string; tile: string }[] = [
  { emoji: '📚', tile: 'bg-emerald-100/80 text-emerald-700' },
  { emoji: '💻', tile: 'bg-blue-100/80 text-blue-700' },
  { emoji: '🖥️', tile: 'bg-indigo-100/80 text-indigo-700' },
  { emoji: '🧴', tile: 'bg-sky-100/80 text-sky-700' },
  { emoji: '👟', tile: 'bg-rose-100/80 text-rose-700' },
  { emoji: '🏀', tile: 'bg-lime-100/80 text-lime-700' },
  { emoji: '💄', tile: 'bg-pink-100/80 text-pink-700' },
  { emoji: '🎸', tile: 'bg-violet-100/80 text-violet-700' },
  { emoji: '🚲', tile: 'bg-amber-100/80 text-amber-700' },
  { emoji: '🎁', tile: 'bg-slate-100 text-slate-600' }
]

const CATEGORY_KEYWORDS: { re: RegExp; index: number }[] = [
  { re: /教材|书籍|图书|书/, index: 0 },
  { re: /数码|电子|手机/, index: 1 },
  { re: /电脑|配件|笔记本|外设/, index: 2 },
  { re: /生活|日用|百货/, index: 3 },
  { re: /服饰|鞋|包|衣/, index: 4 },
  { re: /运动|健身|球/, index: 5 },
  { re: /美妆|护肤|化妆/, index: 6 },
  { re: /乐器|吉他|钢琴|键盘乐/, index: 7 },
  { re: /代步|骑行|自行车|出行|电动车/, index: 8 }
]

const categoryStyle = (name: string, index: number) => {
  const hit = CATEGORY_KEYWORDS.find((item) => item.re.test(name))
  return CATEGORY_STYLES[hit ? hit.index : index % CATEGORY_STYLES.length]
}

/**
 * 商城主页（对齐 Stitch 原型「校园淘 · 首页」的版式；数据契约与行为对齐 Vue 版 HomeView.vue）。
 *
 * 契约要点（**改动视觉时这些一律不动**）：
 * - 四个数据源并发：`GET /categories`、`/recommend/home?limit=8`（游客由后端热门兜底）、
 *   `/recommend/hot?limit=10`、`/goods?pageNum/pageSize=12&sort=latest`
 * - 「最新发布」是**自研分页 12/页**（非通用分页组件），与旧版一致
 * - 侧栏「校园公告」在旧版是静态占位（"公告随运营配置上线"），此处逐字保留
 * - hero 轮播的 4 张图是前端静态常量（见 `HomeHeroCarousel`），不接后台 banners 接口
 */
export default function HomePage() {
  const navigate = useRouteNavigate()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)

  const [latestPage, setLatestPage] = useState(1)
  const [searchText, setSearchText] = useState('')

  const { data: categories = [] } = useListCategoriesQuery()
  const {
    data: hotGoods = [],
    isLoading: hotLoading,
    isFetching: hotFetching,
    refetch: refetchHot
  } = useRecommendHomeQuery(8)
  const { data: hotList = [] } = useRecommendHotQuery(10)
  const { data: latest, isLoading: latestLoading } = usePageGoodsQuery({
    pageNum: latestPage,
    pageSize: PAGE_SIZE.homeLatest,
    sort: 'latest'
  })

  const latestGoods = latest?.list ?? []
  const latestTotal = latest?.total ?? 0
  const latestPages = Math.max(1, Math.ceil(latestTotal / PAGE_SIZE.homeLatest))

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const q = searchText.trim()
    navigate(q ? `/goods?q=${encodeURIComponent(q)}` : '/goods')
  }

  const skeletonGrid = (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="space-y-3 rounded-2xl border border-border bg-card p-2 pb-3">
          <Skeleton className="aspect-[4/3] w-full rounded-xl" />
          <Skeleton className="h-4 w-4/5" />
          <Skeleton className="h-4 w-3/5" />
        </div>
      ))}
    </div>
  )

  return (
    <div className="page space-y-7">
      {/* Hero：4 张主题插画轮播 + 前景文案（搜索状态与跳转逻辑仍在本页） */}
      <HomeHeroCarousel>
        <div className="flex h-full flex-col justify-between p-6 md:p-12">
          <div className="max-w-2xl">
            <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/15 px-3 py-1 text-[11px] font-semibold tracking-wider text-white uppercase backdrop-blur-md">
              <ShieldCheck className="size-3.5 text-[#f2d9a8]" />
              CAMPUS MARKET · 可信的校园闲置交换
            </div>
            <h1 className="text-3xl leading-tight font-extrabold tracking-tight text-white drop-shadow-sm md:text-5xl">
              把用不上的，
              <br />
              <span className="font-black text-[#f2d9a8]">交给刚好需要的人。</span>
            </h1>
            <p className="mt-3 max-w-lg text-[13px] font-medium text-white/85 md:text-base">
              校园认证 · 线下面交 · 不让好东西在角落落灰
            </p>
          </div>

          <div className="mt-5 space-y-4">
            <form
              onSubmit={handleSearch}
              className="flex max-w-md flex-col items-stretch gap-2 rounded-2xl border border-white bg-white/90 p-1.5 shadow-xl backdrop-blur-lg sm:flex-row sm:items-center"
            >
              <div className="flex flex-1 items-center gap-2 px-3">
                <Search className="size-4 shrink-0 text-muted-foreground" />
                <input
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  aria-label="搜索校园好物"
                  placeholder="搜索教材、数码、生活好物"
                  className="w-full border-0 bg-transparent p-1 text-[13px] text-foreground outline-0 placeholder:text-muted-foreground/70"
                />
              </div>
              <button
                type="submit"
                className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-xl bg-primary px-5 py-2.5 text-[13px] font-semibold text-primary-foreground shadow-sm transition hover:bg-primary/90"
              >
                开始寻找
                <ArrowRight className="size-3.5" />
              </button>
            </form>

            <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-[12px] font-medium text-white/90">
              <span className="inline-flex items-center gap-1.5">
                <ShieldCheck className="size-4 text-[#f2d9a8]" />
                校园认证用户
              </span>
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="size-4 text-[#f2d9a8]" />
                约定地点面交
              </span>
              <span className="hidden items-center gap-1.5 sm:inline-flex">
                <HandCoins className="size-4 text-[#f2d9a8]" />
                无中介零手续费
              </span>
            </div>
          </div>
        </div>

        {/* 右下角点缀语（原型同位置，仅大屏显示；纯装饰不参与交互） */}
        <div className="pointer-events-none absolute right-8 bottom-8 hidden text-right select-none lg:block">
          <div className="text-2xl text-white/80 italic drop-shadow">“让校园更有温度，</div>
          <div className="text-2xl font-bold text-[#f2d9a8]/90 italic drop-shadow">让闲置更有价值”</div>
        </div>
      </HomeHeroCarousel>

      {/* 分类导航 */}
      <section className="rounded-2xl border border-border bg-card p-5 shadow-[var(--shadow-paper)]">
        <div className="mb-5 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="h-5 w-1.5 rounded-full bg-primary" aria-hidden />
            <h2 className="text-lg font-bold tracking-tight text-foreground">分类导航</h2>
            <span className="hidden text-xs font-normal text-muted-foreground sm:inline">
              发现更多你需要的商品
            </span>
          </div>
          <button
            type="button"
            onClick={() => navigate('/goods')}
            className="inline-flex shrink-0 items-center gap-0.5 text-xs font-semibold text-muted-foreground transition hover:text-primary"
          >
            查看更多
            <ArrowRight className="size-3.5" />
          </button>
        </div>

        <div className="grid grid-cols-4 gap-2 sm:grid-cols-5 sm:gap-3 lg:grid-cols-10">
          {categories.map((c, index) => {
            const style = categoryStyle(c.name, index)
            return (
              <button
                key={c.id}
                type="button"
                onClick={() => navigate(`/goods?categoryId=${c.id}`)}
                className="group flex flex-col items-center rounded-2xl border border-transparent bg-secondary/40 p-2 text-center transition hover:border-primary/25 hover:bg-primary/5 sm:p-3"
              >
                <span
                  aria-hidden
                  className={`grid size-10 place-items-center rounded-2xl text-xl shadow-sm transition group-hover:scale-110 sm:size-12 sm:text-2xl ${style.tile}`}
                >
                  {style.emoji}
                </span>
                <span className="mt-2 w-full truncate text-[11px] font-bold text-foreground group-hover:text-primary sm:mt-2.5 sm:text-xs">
                  <span className="hidden text-[10px] font-normal text-muted-foreground sm:block">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  {c.name}
                </span>
              </button>
            )
          })}
        </div>
      </section>

      <div className="grid grid-cols-1 items-start gap-7 lg:grid-cols-12">
        <div className="space-y-8 lg:col-span-8 xl:col-span-9">
          {/* 猜你喜欢 */}
          <section className="space-y-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <span
                  aria-hidden
                  className="grid size-6 place-items-center rounded-lg bg-[var(--ginger)]/15 text-[var(--ginger)]"
                >
                  <Flame className="size-4" />
                </span>
                <h2 className="text-lg font-bold text-foreground">猜你喜欢</h2>
                <span className="hidden text-xs text-muted-foreground sm:inline">
                  {isLoggedIn ? '根据你的浏览/收藏/成交推荐' : '登录后为你个性化推荐'}
                </span>
              </div>
              <button
                type="button"
                onClick={() => void refetchHot()}
                disabled={hotFetching}
                className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-semibold text-muted-foreground shadow-sm transition hover:border-primary/30 hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
              >
                <RefreshCw className={`size-3.5 ${hotFetching ? 'animate-spin' : ''}`} />
                换一批
              </button>
            </div>

            {hotLoading ? (
              skeletonGrid
            ) : hotGoods.length ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                {hotGoods.map((g) => (
                  <GoodsCard key={g.id} goods={g} variant="plain" />
                ))}
              </div>
            ) : (
              <EmptyState
                description="还没有商品，快来发布第一件闲置"
                action={
                  <Button variant="outline" size="sm" onClick={() => navigate('/publish')}>
                    去发布
                  </Button>
                }
              />
            )}
          </section>

          {/* 最新发布 */}
          <section className="space-y-4">
            <div className="flex items-center gap-2">
              <span aria-hidden className="grid size-6 place-items-center rounded-lg bg-primary/10 text-primary">
                <Clock3 className="size-4" />
              </span>
              <h2 className="text-lg font-bold text-foreground">最新发布</h2>
            </div>

            {latestLoading ? (
              skeletonGrid
            ) : latestGoods.length ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                {latestGoods.map((g) => (
                  <GoodsCard key={g.id} goods={g} variant="plain" />
                ))}
              </div>
            ) : (
              <EmptyState description="暂无最新发布" />
            )}

            <div className="flex items-center justify-center gap-3 pt-1">
              <Button
                variant="outline"
                size="sm"
                disabled={latestPage <= 1}
                onClick={() => setLatestPage((p) => Math.max(1, p - 1))}
              >
                上一页
              </Button>
              <span className="text-[13px] text-muted-foreground tabular-nums">
                {latestPage} / {latestPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                disabled={latestPage >= latestPages}
                onClick={() => setLatestPage((p) => p + 1)}
              >
                下一页
              </Button>
            </div>
          </section>
        </div>

        {/* 侧栏 */}
        <aside className="space-y-5 lg:col-span-4 xl:col-span-3">
          {/* 校园公告 */}
          <div className="home-notice-card relative overflow-hidden rounded-2xl border border-[var(--ginger)]/35 p-4 shadow-[var(--shadow-paper)]">
            <div className="flex items-center gap-2 text-sm font-bold text-[var(--stamp)]">
              <Megaphone className="size-4 text-[var(--ginger)]" />
              <span>NOTICE 校园公告</span>
            </div>
            <div className="mt-3 flex items-start gap-2 rounded-xl border border-[var(--ginger)]/20 bg-card/85 p-2.5">
              <Megaphone className="mt-0.5 size-3.5 shrink-0 text-[var(--ginger)]" />
              <div>
                <p className="text-xs font-medium text-foreground">公告随运营配置上线</p>
                <p className="mt-0.5 text-[11px] leading-relaxed text-muted-foreground">
                  防网络诈骗提醒：严禁脱离本平台转账汇款，提倡校园公共区域当面交接！
                </p>
              </div>
            </div>
            <Megaphone
              aria-hidden
              className="pointer-events-none absolute -right-2 -bottom-2 size-20 text-[var(--ginger)]/10"
            />
          </div>

          {/* 正在被关注 */}
          <Card className="p-5">
            <div className="mb-4 flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className={KICKER}>TRENDING</span>
                <b className="text-sm font-bold text-foreground">正在被关注</b>
              </div>
              <span aria-hidden className="size-2 animate-pulse rounded-full bg-[var(--terracotta)]" />
            </div>

            {hotList.length ? (
              <ol className="space-y-3.5">
                {hotList.map((g, idx) => (
                  <li key={g.id}>
                    <button
                      type="button"
                      onClick={() => navigate(`/goods/${g.id}`)}
                      className="group flex w-full items-center justify-between gap-2 text-left"
                    >
                      <span className="flex min-w-0 items-center gap-2.5">
                        <span
                          className={
                            idx === 0
                              ? 'grid size-5 shrink-0 place-items-center rounded-md bg-[var(--ginger)] text-[11px] font-bold text-white'
                              : idx === 1
                                ? 'grid size-5 shrink-0 place-items-center rounded-md bg-[var(--ginger)]/25 text-[11px] font-bold text-[var(--stamp)]'
                                : idx === 2
                                  ? 'grid size-5 shrink-0 place-items-center rounded-md border border-[var(--ginger)]/30 bg-[var(--ginger)]/10 text-[11px] font-bold text-[var(--stamp)]'
                                  : 'grid size-5 shrink-0 place-items-center rounded-md bg-secondary text-[11px] font-semibold text-muted-foreground'
                          }
                        >
                          {idx + 1}
                        </span>
                        <span className="truncate text-xs font-medium text-foreground group-hover:text-primary">
                          {g.title}
                        </span>
                      </span>
                      <span className="flex shrink-0 items-center gap-1 pl-2 text-[11px] font-semibold text-[var(--terracotta)] tabular-nums">
                        <Flame className="size-3" />
                        {g.viewCount ?? 0}
                      </span>
                    </button>
                  </li>
                ))}
              </ol>
            ) : (
              <div className="flex items-center gap-2 text-[13px] text-muted-foreground">
                <TrendingUp className="size-4" />
                <span>暂无热度数据</span>
              </div>
            )}

            <div className="mt-4 border-t border-border pt-3 text-right">
              <button
                type="button"
                onClick={() => navigate('/goods')}
                className="inline-flex items-center gap-0.5 text-xs font-semibold text-primary transition hover:text-primary/80"
              >
                查看更多
                <ArrowRight className="size-3.5" />
              </button>
            </div>
          </Card>

          {/* 安全护盾 */}
          <div className="relative overflow-hidden rounded-2xl bg-primary p-5 text-primary-foreground shadow-[var(--shadow-paper)]">
            <div className="mb-3 flex items-center gap-2">
              <ShieldHalf className="size-5 text-[#f2d9a8]" />
              <h4 className="text-sm font-bold">校园交易安全护盾</h4>
            </div>
            <p className="mb-4 text-xs leading-relaxed text-primary-foreground/75">
              校园淘仅面向本校在读师生开放，实名校内邮箱认证，线下同校面交更安心。
            </p>
            <div className="grid grid-cols-2 gap-2 text-[11px] font-medium">
              <div className="flex items-center gap-1.5 rounded-lg bg-white/10 px-2 py-1.5">
                <ShieldCheck className="size-3.5 text-[#f2d9a8]" />
                学号实名认证
              </div>
              <div className="flex items-center gap-1.5 rounded-lg bg-white/10 px-2 py-1.5">
                <MapPin className="size-3.5 text-[#f2d9a8]" />
                线下当面交易
              </div>
              <div className="col-span-2 flex items-center gap-1.5 rounded-lg bg-white/10 px-2 py-1.5">
                <HandCoins className="size-3.5 text-[#f2d9a8]" />
                平台不经手资金
              </div>
            </div>
            <ShieldHalf
              aria-hidden
              className="pointer-events-none absolute -right-3 -bottom-3 size-20 text-white/10"
            />
          </div>
        </aside>
      </div>
    </div>
  )
}
