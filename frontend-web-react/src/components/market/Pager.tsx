import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * 数字分页器（三页共用，纯展示）。
 *
 * 与旧版的唯一差别是**外观**：旧版只有「上一页 / x / y / 下一页」，
 * 原型里是「‹ 1 2 3 … 96 ›」。翻页仍然只调用调用方传入的 `onChange(page)`，
 * 页码口径、total 与 pageCount 的算法一律留在页面里，本组件不做任何计算。
 */
export function Pager({
  page,
  pageCount,
  total,
  unit = '件',
  onChange,
  className
}: {
  page: number
  pageCount: number
  total?: number
  unit?: string
  onChange: (nextPage: number) => void
  className?: string
}) {
  if (pageCount <= 1) return null

  const pages = buildPages(page, pageCount)

  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-3', className)}>
      {typeof total === 'number' ? (
        <span className="text-[12px] text-muted-foreground">
          共 <b className="text-foreground tabular-nums">{total}</b> {unit}
        </span>
      ) : (
        <span />
      )}

      <nav aria-label="分页" className="flex items-center gap-1">
        <button
          type="button"
          aria-label="上一页"
          disabled={page <= 1}
          onClick={() => onChange(page - 1)}
          className="grid size-8 place-items-center rounded-lg border border-border bg-card text-muted-foreground transition hover:border-primary/30 hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
        >
          <ChevronLeft className="size-4" />
        </button>

        {pages.map((item, index) =>
          item === 'gap' ? (
            <span key={`gap-${index}`} className="px-1 text-[12px] text-muted-foreground">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              aria-current={item === page ? 'page' : undefined}
              onClick={() => onChange(item)}
              className={cn(
                'grid h-8 min-w-8 place-items-center rounded-lg border px-2 text-[12px] tabular-nums transition',
                item === page
                  ? 'border-primary bg-primary font-semibold text-primary-foreground'
                  : 'border-border bg-card text-muted-foreground hover:border-primary/30 hover:text-primary'
              )}
            >
              {item}
            </button>
          )
        )}

        <button
          type="button"
          aria-label="下一页"
          disabled={page >= pageCount}
          onClick={() => onChange(page + 1)}
          className="grid size-8 place-items-center rounded-lg border border-border bg-card text-muted-foreground transition hover:border-primary/30 hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
        >
          <ChevronRight className="size-4" />
        </button>
      </nav>
    </div>
  )
}

/** 生成「1 … 4 5 6 … 96」这类页码序列（含 `gap` 占位） */
function buildPages(page: number, pageCount: number): (number | 'gap')[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, i) => i + 1)

  const keep = new Set<number>([1, pageCount, page, page - 1, page + 1])
  if (page <= 3) [2, 3, 4].forEach((n) => keep.add(n))
  if (page >= pageCount - 2) [pageCount - 1, pageCount - 2, pageCount - 3].forEach((n) => keep.add(n))

  const sorted = [...keep].filter((n) => n >= 1 && n <= pageCount).sort((a, b) => a - b)
  const out: (number | 'gap')[] = []
  sorted.forEach((n, i) => {
    if (i > 0 && n - (sorted[i - 1] as number) > 1) out.push('gap')
    out.push(n)
  })
  return out
}

export default Pager
