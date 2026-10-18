import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn } from '@/features/auth/authSlice'
import {
  useGetSwapPostQuery,
  usePublishSwapPostMutation,
  useUpdateSwapPostMutation
} from '@/api/swapApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { CategoryCascader } from '@/components/CategoryCascader'
import { cn } from '@/lib/utils'
import type { SwapPostPublishDTO } from '@/api/types'

/** 校验规则与提示文案与 Vue 版 SwapPublishView 的 el-form rules 逐字一致（diffAmount 无 rules，走提交前手工校验） */
const schema = z.object({
  title: z.string().min(1, '请输入标题').max(50),
  myItemDesc: z.string().min(1, '请描述你的物品').max(500),
  wantItemDesc: z.string().min(1, '请描述想要的物品').max(500),
  diffAmount: z.string().optional()
})

type FormValues = z.infer<typeof schema>

/**
 * 发布/编辑交换（PRD SWP-01、§5.5）。
 *
 * 与 Vue 版对齐的三重差价逻辑：
 * 1. 开关是**数字 1/0**（不是 boolean）
 * 2. 关闭开关时清空金额（`watch(() => form.allowDiff)` 的作用）
 * 3. 提交前手工校验：`allowDiff === 1 && diffAmount == null` → 「接受补差价时请填写期望差价金额」
 */
export default function SwapPublishPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)

  const editId = Number(searchParams.get('id')) || null
  const isEdit = editId !== null

  const [categoryPath, setCategoryPath] = useState<number[]>([])
  const [allowDiff, setAllowDiff] = useState<0 | 1>(0)

  const { data: editing } = useGetSwapPostQuery(editId as number, { skip: !isEdit })
  const [publishSwapPost, { isLoading: publishing }] = usePublishSwapPostMutation()
  const [updateSwapPost, { isLoading: updating }] = useUpdateSwapPostMutation()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: '', myItemDesc: '', wantItemDesc: '', diffAmount: '' }
  })

  useEffect(() => {
    if (!isLoggedIn) {
      navigate(`/login?redirect=${encodeURIComponent(isEdit ? `/swap/publish?id=${editId}` : '/swap/publish')}`, {
        replace: true
      })
    }
  }, [isLoggedIn, isEdit, editId, navigate])

  useEffect(() => {
    if (!editing) return
    setCategoryPath(editing.parentCategoryId ? [editing.parentCategoryId, editing.categoryId] : [editing.categoryId])
    setAllowDiff((editing.allowDiff ?? 0) as 0 | 1)
    form.reset({
      title: editing.title,
      myItemDesc: editing.myItemDesc,
      wantItemDesc: editing.wantItemDesc,
      diffAmount:
        editing.diffAmount === null || editing.diffAmount === undefined ? '' : String(Number(editing.diffAmount))
    })
  }, [editing, form])

  /** 关闭差价开关时清空金额（对应旧版 watch） */
  const toggleDiff = (next: boolean) => {
    const value: 0 | 1 = next ? 1 : 0
    setAllowDiff(value)
    if (value === 0) form.setValue('diffAmount', '')
  }

  const onSubmit = form.handleSubmit(async (values) => {
    if (!categoryPath.length) {
      toast.warning('请选择物品分类')
      return
    }
    const diffText = (values.diffAmount ?? '').trim()
    if (allowDiff === 1 && diffText === '') {
      toast.warning('接受补差价时请填写期望差价金额')
      return
    }
    const payload: SwapPostPublishDTO = {
      title: values.title,
      categoryId: categoryPath[categoryPath.length - 1],
      myItemDesc: values.myItemDesc,
      wantItemDesc: values.wantItemDesc,
      allowDiff,
      diffAmount: allowDiff === 1 ? Number(diffText) : null
    }
    try {
      if (isEdit && editId) {
        await updateSwapPost({ id: editId, body: payload }).unwrap()
        toast.success('修改已保存')
        navigate(`/swap/${editId}`)
      } else {
        const newId = await publishSwapPost(payload).unwrap()
        toast.success('发布成功')
        navigate(`/swap/${newId}`)
      }
    } catch (err) {
      if (((err as Error)?.message || '').includes('认证')) navigate('/verify')
    }
  })

  return (
    <div className="page">
      <h2 className="page-title">{isEdit ? '编辑交换' : '发布交换'}</h2>

      <Card className="paper-card max-w-[760px] p-6">
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1.5">
            <Label>物品分类</Label>
            <CategoryCascader value={categoryPath} onChange={setCategoryPath} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="title">标题</Label>
            <Input id="title" maxLength={50} placeholder="如：九成新山地车换平板" {...form.register('title')} />
            {form.formState.errors.title ? (
              <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="myItemDesc">我的物品</Label>
            <Textarea
              id="myItemDesc"
              rows={4}
              maxLength={500}
              placeholder="描述你拿出的物品：名称、成色、使用情况、瑕疵等"
              {...form.register('myItemDesc')}
            />
            {form.formState.errors.myItemDesc ? (
              <p className="text-xs text-destructive">{form.formState.errors.myItemDesc.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="wantItemDesc">想要的物品</Label>
            <Textarea
              id="wantItemDesc"
              rows={4}
              maxLength={500}
              placeholder="描述你希望换到的物品与可接受范围"
              {...form.register('wantItemDesc')}
            />
            {form.formState.errors.wantItemDesc ? (
              <p className="text-xs text-destructive">{form.formState.errors.wantItemDesc.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label>接受补差价</Label>
            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                role="switch"
                aria-checked={allowDiff === 1}
                onClick={() => toggleDiff(allowDiff === 0)}
                className={cn(
                  'relative h-6 w-11 rounded-full transition',
                  allowDiff === 1 ? 'bg-primary' : 'bg-secondary'
                )}
              >
                <span
                  className={cn(
                    'absolute top-0.5 size-5 rounded-full bg-card shadow-sm transition-all',
                    allowDiff === 1 ? 'left-[22px]' : 'left-0.5'
                  )}
                />
              </button>
              <span className="text-[12px] text-muted-foreground">
                {allowDiff === 1 ? '对方可补差价给你（或你补差价给对方，具体线下协商）' : '只接受等值交换'}
              </span>
            </div>
          </div>

          {allowDiff === 1 ? (
            <div className="space-y-1.5">
              <Label htmlFor="diffAmount">期望差价</Label>
              <div className="flex items-center gap-3">
                <Input
                  id="diffAmount"
                  type="number"
                  min={0}
                  step={10}
                  className="w-[200px]"
                  {...form.register('diffAmount')}
                />
                <span className="text-[12px] text-muted-foreground">元，填 0 表示可协商</span>
              </div>
            </div>
          ) : null}

          <div className="flex gap-3">
            <Button type="submit" size="lg" disabled={publishing || updating}>
              {publishing || updating ? <Loader2 className="size-4 animate-spin" /> : null}
              {isEdit ? '保存修改' : '发 布'}
            </Button>
            <Button type="button" size="lg" variant="outline" onClick={() => navigate(-1)}>
              取消
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
