import { cn } from '@/lib/utils'

/**
 * 市场三页（商品专区 / 求购广场 / 以物交换）共用的**纯展示**小件。
 *
 * 设计来源：Stitch 原型里这三页的筛选面板、药丸筛选块、段控与分页都是同一套形态
 * （白卡 `rounded-2xl` + 极轻投影、药丸 chip、`inline-flex p-0.5` 段控）。
 * 这里只抽外观，不承载任何数据逻辑：所有状态仍由各页面自己持有，
 * 组件只接收 `active` / `value` 与 `onClick` / `onChange`。
 */

/** 白卡面板：筛选区、侧栏卡共用 */
export function Panel({
  className,
  children,
  ...rest
}: React.ComponentProps<'section'>) {
  return (
    <section
      className={cn('rounded-2xl border border-border bg-card shadow-[var(--shadow-paper)]', className)}
      {...rest}
    >
      {children}
    </section>
  )
}

/** 区块小标题：左竖条 + 中文 + 英文副标（原型「一级类目 / CATEGORY」） */
export function SectionLabel({
  title,
  en,
  className
}: {
  title: string
  en?: string
  className?: string
}) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <span aria-hidden className="h-4 w-1 rounded-full bg-primary" />
      <span className="text-[13px] font-bold text-foreground">{title}</span>
      {en ? <span className="text-[11px] tracking-wide text-muted-foreground uppercase">/ {en}</span> : null}
    </div>
  )
}

/** 药丸筛选块（分类、预算区间、成色都走它） */
export function Pill({
  active,
  className,
  children,
  ...rest
}: React.ComponentProps<'button'> & { active?: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-[12px] font-medium whitespace-nowrap transition',
        active
          ? 'bg-primary text-primary-foreground shadow-sm'
          : 'bg-secondary/60 text-muted-foreground hover:bg-secondary hover:text-foreground',
        className
      )}
      {...rest}
    >
      {children}
    </button>
  )
}

/** 段控（状态 Tab / 排序 / 全部-教材专区 二元切换） */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  className,
  size = 'md'
}: {
  value: T
  options: { value: T; label: string }[]
  onChange: (next: T) => void
  className?: string
  size?: 'sm' | 'md'
}) {
  return (
    <div
      role="tablist"
      className={cn('inline-flex rounded-lg bg-secondary/60 p-0.5', className)}
    >
      {options.map((opt) => {
        const active = opt.value === value
        return (
          <button
            key={opt.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(opt.value)}
            className={cn(
              'rounded-md font-medium whitespace-nowrap transition',
              size === 'sm' ? 'px-2.5 py-1 text-[11px]' : 'px-3 py-1.5 text-[12px]',
              active
                ? 'bg-card text-primary shadow-xs font-semibold'
                : 'text-muted-foreground hover:text-foreground'
            )}
          >
            {opt.label}
          </button>
        )
      })}
    </div>
  )
}

/** 筛选行标签：`Sort:` / `Status:` 这类前置小字 */
export function FieldText({ icon, children }: { icon?: React.ReactNode; children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 text-[12px] text-muted-foreground">
      {icon}
      {children}
    </span>
  )
}

/** 静态保障标（原型「校内当面验货 / 官方学生实名 / …」） */
export function TrustChip({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-border bg-secondary/40 px-2.5 py-1 text-[11px] text-muted-foreground">
      {children}
    </span>
  )
}
