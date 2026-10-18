import { cn } from '@/lib/utils'

/**
 * 价格统一展示（对齐 Vue 版 components/PriceText.vue）：
 * - `0` → 「免费赠送」（绿色）
 * - 其它 → `¥` + 千分位两位小数
 * - `size='large'` 用于详情页主价格（28px）
 */
export function PriceText({
  value,
  size = 'normal',
  className
}: {
  value?: number | string | null
  size?: 'normal' | 'large'
  className?: string
}) {
  const n = Number(value ?? 0)
  const sizeClass = size === 'large' ? 'text-[28px]' : 'text-[15px]'

  if (!Number.isFinite(n) || n === 0) {
    return <span className={cn('font-semibold text-[var(--success)]', sizeClass, className)}>免费赠送</span>
  }

  const text = n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return (
    <span className={cn('font-semibold text-[var(--terracotta)] tabular-nums', sizeClass, className)}>
      <span className="mr-0.5 text-[0.72em]">¥</span>
      {text}
    </span>
  )
}

export default PriceText
