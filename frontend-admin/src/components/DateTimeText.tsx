import { cn } from '@/lib/utils'
import { formatDateTime } from '@/lib/format'

interface DateTimeTextProps {
  value?: string | null
  mode?: 'datetime' | 'date'
  className?: string
}

/** 时间展示：与旧版口径一致（ISO → yyyy-MM-dd HH:mm / yyyy-MM-dd） */
export function DateTimeText({ value, mode = 'datetime', className }: DateTimeTextProps) {
  return <span className={cn('tabular-nums', className)}>{formatDateTime(value, mode)}</span>
}
