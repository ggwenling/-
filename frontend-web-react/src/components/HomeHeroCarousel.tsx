import { useEffect, useState } from 'react'
import { cn } from '@/lib/utils'

/**
 * 首页 hero 轮播（视觉对齐 Stitch 原型 `data-purpose="hero-carousel"`）。
 *
 * 职责边界（重要）：
 * - **只负责背景图轮播 + 指示点**，不持有搜索状态、不做路由跳转、不发请求；
 *   搜索/信任点等前景文案由 `HomePage` 通过 `children` 传入，业务逻辑留在页面里。
 * - 4 张图是前端静态常量（不接 `/api/admin/banners`，避免与后台运营配置抢数据源）。
 * - 图片来源：Stitch 原型项目里那 4 张 1376×768 主题插画，已落地到 `/public/banners/`。
 */

export interface HeroSlide {
  src: string
  alt: string
  /** 主题名，用于指示点的 aria-label 与无障碍播报 */
  label: string
}

export const HERO_SLIDES: HeroSlide[] = [
  { src: '/banners/banner-1-books.jpg', alt: '同学在校园绿荫下交换教材与书籍', label: '教材书籍流转' },
  { src: '/banners/banner-2-mobility.jpg', alt: '同学骑着自行车穿行在校园道路', label: '校园骑行出行' },
  { src: '/banners/banner-3-handover.jpg', alt: '两位同学在校园长椅旁当面交付物品', label: '校内当面交易' },
  { src: '/banners/banner-4-clearance.jpg', alt: '毕业季同学在草坪上整理闲置物品', label: '宿舍清仓好物' }
]

/** 自动轮播间隔：6s（原型节奏） */
const INTERVAL_MS = 6000

export function HomeHeroCarousel({
  children,
  className
}: {
  children: React.ReactNode
  className?: string
}) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const [reducedMotion, setReducedMotion] = useState(false)

  // 尊重系统「减少动态效果」：不做自动轮播，也不做淡入
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    const sync = () => setReducedMotion(mq.matches)
    sync()
    mq.addEventListener('change', sync)
    return () => mq.removeEventListener('change', sync)
  }, [])

  // 自动轮播：hover / 键盘聚焦时暂停，卸载时清定时器
  useEffect(() => {
    if (reducedMotion || paused) return
    const timer = window.setInterval(
      () => setIndex((prev) => (prev + 1) % HERO_SLIDES.length),
      INTERVAL_MS
    )
    return () => window.clearInterval(timer)
  }, [reducedMotion, paused])

  return (
    <section
      aria-label="首页主推位轮播"
      className={cn(
        'relative overflow-hidden rounded-[26px] border border-primary/15 bg-primary shadow-[var(--shadow-lift)]',
        className
      )}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
    >
      <div className="relative h-[340px] w-full md:h-[400px]">
        {/* 背景图层：4 张叠放交叉淡入；高度写死避免 CLS；图挂了也还有 bg-primary 兜底 */}
        {HERO_SLIDES.map((slide, i) => (
          <img
            key={slide.src}
            src={slide.src}
            alt={slide.alt}
            loading={i === 0 ? 'eager' : 'lazy'}
            fetchPriority={i === 0 ? 'high' : 'auto'}
            decoding="async"
            aria-hidden={i !== index}
            className={cn(
              'absolute inset-0 size-full object-cover transition-opacity duration-700 motion-reduce:transition-none',
              i === index ? 'opacity-100' : 'opacity-0'
            )}
          />
        ))}

        {/* 双层遮罩：左侧重（保文字可读）+ 底部重（托住指示点） */}
        <div className="home-hero-scrim pointer-events-none absolute inset-0" aria-hidden />

        {/* 前景文案由页面传入，保持业务逻辑在 HomePage */}
        <div className="relative z-10 h-full">{children}</div>

        {/* 指示点 */}
        <div className="absolute bottom-4 left-1/2 z-20 flex -translate-x-1/2 items-center gap-1.5 rounded-full bg-black/25 px-3 py-1 backdrop-blur-sm">
          {HERO_SLIDES.map((slide, i) => (
            <button
              key={slide.src}
              type="button"
              onClick={() => setIndex(i)}
              aria-label={`第 ${i + 1} 张：${slide.label}`}
              aria-current={i === index ? 'true' : undefined}
              className={cn(
                'h-1.5 rounded-full transition-all',
                i === index ? 'w-6 bg-white' : 'w-2 bg-white/45 hover:bg-white/75'
              )}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

export default HomeHeroCarousel
