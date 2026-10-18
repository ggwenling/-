import { useMemo } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

interface PaginationProps {
  pageNum: number
  pageSize: number
  total: number
  onPageChange: (pageNum: number, pageSize: number) => void
  pageSizeOptions?: number[]
  className?: string
}

/** 全后台统一分页（默认 20 条/页，与旧版 ProPagination 口径一致） */
export function Pagination({
  pageNum,
  pageSize,
  total,
  onPageChange,
  pageSizeOptions = [10, 20, 50, 100],
  className
}: PaginationProps) {
  const pageCount = Math.max(1, Math.ceil(total / Math.max(1, pageSize)))

  const pages = useMemo(() => {
    const list: number[] = []
    const max = 5
    let start = Math.max(1, pageNum - Math.floor(max / 2))
    const end = Math.min(pageCount, start + max - 1)
    start = Math.max(1, end - max + 1)
    for (let i = start; i <= end; i++) list.push(i)
    return list
  }, [pageNum, pageCount])

  const go = (next: number) => {
    const clamped = Math.min(Math.max(1, next), pageCount)
    if (clamped !== pageNum) onPageChange(clamped, pageSize)
  }

  return (
    <div
      className={cn(
        'mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-dashed border-border pt-4',
        className
      )}
    >
      <span className="text-xs text-muted-foreground">
        共 <span className="font-semibold text-foreground tabular-nums">{total}</span> 条
      </span>

      <div className="flex items-center gap-2">
        <Select
          value={String(pageSize)}
          onValueChange={(v) => onPageChange(1, Number(v))}
        >
          <SelectTrigger size="sm" className="w-[104px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {pageSizeOptions.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size} 条/页
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Button variant="outline" size="icon" className="size-8" disabled={pageNum <= 1} onClick={() => go(pageNum - 1)}>
          <ChevronLeft />
        </Button>

        {pages.map((p) => (
          <Button
            key={p}
            variant={p === pageNum ? 'default' : 'outline'}
            size="icon"
            className="size-8 tabular-nums"
            onClick={() => go(p)}
          >
            {p}
          </Button>
        ))}

        <Button
          variant="outline"
          size="icon"
          className="size-8"
          disabled={pageNum >= pageCount}
          onClick={() => go(pageNum + 1)}
        >
          <ChevronRight />
        </Button>
      </div>
    </div>
  )
}
