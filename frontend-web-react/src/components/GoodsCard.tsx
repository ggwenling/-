import { Eye, Heart } from 'lucide-react'
import { RouteLink } from '@/components/RouteLink'
import { cn } from '@/lib/utils'
import { CONDITION_TEXT, GOODS_STATUS_TEXT } from '@/lib/constants'
import { PriceText } from './PriceText'
import { Badge } from '@/components/ui/badge'
import type { GoodsCardVO, GoodsTagLike } from '@/api/types'

/** 标签兼容两种后端结构：列表接口是字符串，详情/发布接口是 `{id,name}` */
const tagName = (tag: GoodsTagLike): string => (typeof tag === 'string' ? tag : (tag?.name ?? ''))

/** 卡面形态：`paper` = 全站默认的拍立得纸感卡；`plain` = 首页专用的白卡（对齐 Stitch 原型） */
export type GoodsCardVariant = 'paper' | 'plain'

/**
 * 全站复用商品卡（对齐 Vue 版 components/GoodsCard.vue 的结构与文案）。
 * 视觉默认是「拍立得」纸感卡：白边 + 折角 + 悬停回正上浮。
 *
 * `variant='plain'` 是**纯展示差异**：白底圆角 + 1px 描边 + 悬停上浮，供首页商品流使用；
 * 默认值仍是 `paper`，因此商品列表 / 详情相似推荐 / 个人中心等调用方零改动、零回归。
 */
export function GoodsCard({
  goods,
  className,
  variant = 'paper'
}: {
  goods: GoodsCardVO
  className?: string
  variant?: GoodsCardVariant
}) {
  const conditionText = CONDITION_TEXT[Number(goods.conditionLevel)] || '—'
  const statusText = GOODS_STATUS_TEXT[goods.status || ''] || ''
  const tags = goods.tags || []
  const shortTime = goods.createdAt ? String(goods.createdAt).slice(5, 10) : ''

  const cover =
    goods.coverUrl ? (
      <img
        src={goods.coverUrl}
        alt=""
        loading="lazy"
        className={cn(
          'block aspect-[4/3] w-full bg-secondary object-cover',
          variant === 'plain' ? 'transition-transform duration-500 group-hover:scale-105' : 'rounded-[4px]'
        )}
      />
    ) : (
      <div
        className={cn(
          'flex aspect-[4/3] w-full items-center justify-center bg-secondary/60 text-[13px] text-muted-foreground',
          variant === 'paper' && 'rounded-[4px]'
        )}
      >
        暂无图片
      </div>
    )

  const statusBadge =
    goods.status && goods.status !== 'ON_SALE' ? (
      <Badge
        variant="outline"
        className={cn(
          'absolute top-2 right-2',
          variant === 'plain'
            ? 'border-border bg-card/95 text-[11px] font-semibold text-foreground'
            : 'border-border bg-card/90'
        )}
      >
        {statusText}
      </Badge>
    ) : null

  if (variant === 'plain') {
    return (
      <RouteLink
        to={`/goods/${goods.id}`}
        className={cn(
          'group flex flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-[var(--shadow-paper)] transition duration-300 focus-visible:ring-2 focus-visible:ring-ring/40 hover:-translate-y-0.5 hover:shadow-[var(--shadow-lift)]',
          className
        )}
      >
        <div className="relative overflow-hidden bg-secondary">
          {cover}
          {statusBadge}
        </div>

        <div className="flex flex-1 flex-col justify-between p-3.5">
          <div>
            <h3 className="line-clamp-1 text-[14px] font-semibold text-foreground transition-colors group-hover:text-primary">
              {goods.title}
            </h3>
            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
              <span className="rounded-md bg-secondary px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">
                {conditionText}
              </span>
              {tags.slice(0, 2).map((tag) => (
                <span
                  key={tagName(tag)}
                  className="rounded-md bg-[var(--ginger)]/15 px-1.5 py-0.5 text-[11px] font-medium text-[var(--stamp)]"
                >
                  {tagName(tag)}
                </span>
              ))}
              {tags.length > 2 ? (
                <span className="text-[11px] text-muted-foreground">+{tags.length - 2}</span>
              ) : null}
            </div>
          </div>

          <div className="mt-3 flex items-baseline justify-between border-t border-border pt-2">
            <PriceText value={goods.price} />
            <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
              <Eye className="size-3.5" />
              {goods.viewCount ?? 0}
            </span>
          </div>
        </div>
      </RouteLink>
    )
  }

  return (
    <RouteLink
      to={`/goods/${goods.id}`}
      className={cn('polaroid-card group block focus-visible:ring-2 focus-visible:ring-ring/40', className)}
    >
      <div className="relative">
        {cover}
        {statusBadge}
      </div>

      <div className="px-1 pt-3 pb-1">
        <div className="line-clamp-2 h-[42px] text-[14px] leading-[1.5] font-semibold text-foreground">
          {goods.title}
        </div>
        <div className="mt-1.5">
          <PriceText value={goods.price} />
        </div>
        <div className="my-3 flex flex-wrap items-center gap-1.5">
          <Badge variant="soft">{conditionText}</Badge>
          {tags.slice(0, 3).map((tag) => (
            <Badge key={tagName(tag)} variant="accent">
              {tagName(tag)}
            </Badge>
          ))}
          {tags.length > 3 ? <span className="text-[12px] text-muted-foreground">+{tags.length - 3}</span> : null}
        </div>
        <div className="flex items-center gap-3.5 text-[12px] text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <Eye className="size-3.5" />
            {goods.viewCount ?? 0}
          </span>
          <span className="inline-flex items-center gap-1">
            <Heart className="size-3.5" />
            {goods.wantCount ?? 0}
          </span>
          <span className="ml-auto">{shortTime}</span>
        </div>
      </div>
    </RouteLink>
  )
}

export default GoodsCard
