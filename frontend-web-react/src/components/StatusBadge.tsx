import { Badge } from '@/components/ui/badge'

type BadgeVariant =
  | 'default'
  | 'secondary'
  | 'destructive'
  | 'outline'
  | 'success'
  | 'warning'
  | 'info'
  | 'accent'
  | 'soft'

const VARIANTS: BadgeVariant[] = [
  'default',
  'secondary',
  'destructive',
  'outline',
  'success',
  'warning',
  'info',
  'accent',
  'soft'
]

function toVariant(tone?: string): BadgeVariant {
  return (VARIANTS as string[]).includes(tone || '') ? (tone as BadgeVariant) : 'soft'
}

interface StatusBadgeProps {
  label: string
  tone?: string
  className?: string
}

/** 通用状态徽标：tone 为语义色名（success/warning/info/destructive/soft/...） */
export function StatusBadge({ label, tone, className }: StatusBadgeProps) {
  return (
    <Badge variant={toVariant(tone)} className={className}>
      {label}
    </Badge>
  )
}
