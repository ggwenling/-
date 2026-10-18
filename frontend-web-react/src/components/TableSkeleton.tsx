import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface TableSkeletonProps {
  rows?: number
  className?: string
}

/** 列表加载骨架屏（禁止白屏） */
export function TableSkeleton({ rows = 6, className }: TableSkeletonProps) {
  return (
    <div className={cn('space-y-2.5 py-3', className)}>
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-10 w-full rounded-md" />
      ))}
    </div>
  )
}
