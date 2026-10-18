import { ChevronDown } from 'lucide-react'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { useListCategoriesQuery } from '@/api/goodsApi'
import type { CategoryTreeVO } from '@/api/types'

/**
 * 二级分类级联选择（替代 Element Plus 的 `el-cascader`，后端只支持两级）。
 *
 * 契约：提交时取**末级分类 id**（`categoryPath[length-1]`）；回填时
 * `parentCategoryId ? [parentCategoryId, categoryId] : [categoryId]`。
 * 一级分类的 `children` 为启用中的二级分类（`GET /categories` 已是二级树）。
 */
export function CategoryCascader({
  value,
  onChange,
  placeholder = '选择二级分类',
  className
}: {
  /** 路径数组：`[一级id]` 或 `[一级id, 二级id]` */
  value: number[]
  onChange: (path: number[]) => void
  placeholder?: string
  className?: string
}) {
  const { data: categories = [] } = useListCategoriesQuery()

  const first = categories.find((c) => c.id === value[0])
  const second = first?.children?.find((c) => c.id === value[1])
  const label = second ? `${first?.name} / ${second.name}` : (first?.name ?? '')

  const pickFirst = (c: CategoryTreeVO) => {
    // 选一级分类：若其下有二级分类，先只落一级，等用户点二级；无二级则直接落一级
    const children = c.children ?? []
    onChange(children.length ? [c.id] : [c.id])
  }

  const pickSecond = (parent: CategoryTreeVO, child: CategoryTreeVO) => {
    onChange([parent.id, child.id])
  }

  const activeParent = first ?? categories[0]

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          type="button"
          className={cn(
            'flex h-9 w-full items-center justify-between gap-2 rounded-lg border border-input bg-card px-3 text-left text-sm shadow-xs transition hover:bg-secondary/40',
            className
          )}
        >
          <span className={label ? 'truncate text-foreground' : 'truncate text-muted-foreground'}>
            {label || placeholder}
          </span>
          <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
        </button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-[420px] p-0">
        <div className="grid grid-cols-2 gap-0 divide-x divide-border">
          <div className="max-h-72 overflow-y-auto p-2">
            {categories.map((c) => (
              <button
                key={c.id}
                type="button"
                onClick={() => pickFirst(c)}
                className={cn(
                  'block w-full truncate rounded-md px-2.5 py-2 text-left text-[13px] transition',
                  activeParent?.id === c.id
                    ? 'bg-secondary/70 font-semibold text-primary'
                    : 'text-foreground hover:bg-secondary/50'
                )}
              >
                {c.name}
              </button>
            ))}
          </div>
          <div className="max-h-72 overflow-y-auto p-2">
            {(activeParent?.children ?? []).length ? (
              activeParent!.children!.map((child) => (
                <button
                  key={child.id}
                  type="button"
                  onClick={() => pickSecond(activeParent!, child)}
                  className={cn(
                    'block w-full truncate rounded-md px-2.5 py-2 text-left text-[13px] transition',
                    value[1] === child.id
                      ? 'bg-secondary/70 font-semibold text-primary'
                      : 'text-foreground hover:bg-secondary/50'
                  )}
                >
                  {child.name}
                </button>
              ))
            ) : (
              <p className="px-2.5 py-2 text-[12px] text-muted-foreground">该分类下暂无子分类，可直接选中</p>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}

export default CategoryCascader
