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
  useGetWantPostQuery,
  usePublishWantPostMutation,
  useUpdateWantPostMutation
} from '@/api/wantApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { CategoryCascader } from '@/components/CategoryCascader'
import type { WantPostPublishDTO } from '@/api/types'

/** 校验规则与提示文案与 Vue 版 WantPublishView 的 el-form rules 逐字一致 */
const schema = z.object({
  title: z.string().min(1, '请输入标题').max(50),
  description: z.string().min(1, '请输入求购描述').max(500),
  budget: z.string().optional()
})

type FormValues = z.infer<typeof schema>

/**
 * 发布/编辑求购（PRD REQ-01/02、§5.4，编辑复用路由 + `?id=`）。
 *
 * 与 Vue 版对齐：单栏表单（期望分类/标题/心理价/求购描述）+「取消」返回上一页；
 * 心理价**留空表示价格面议**（提交 `null`，后端不用 0 表示面议）；
 * 失败文案含「认证」→ 跳 `/verify`。
 */
export default function WantPublishPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)

  const editId = Number(searchParams.get('id')) || null
  const isEdit = editId !== null

  const [categoryPath, setCategoryPath] = useState<number[]>([])
  const { data: editing } = useGetWantPostQuery(editId as number, { skip: !isEdit })
  const [publishWantPost, { isLoading: publishing }] = usePublishWantPostMutation()
  const [updateWantPost, { isLoading: updating }] = useUpdateWantPostMutation()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: '', description: '', budget: '' }
  })

  useEffect(() => {
    if (!isLoggedIn) {
      navigate(`/login?redirect=${encodeURIComponent(isEdit ? `/want/publish?id=${editId}` : '/want/publish')}`, {
        replace: true
      })
    }
  }, [isLoggedIn, isEdit, editId, navigate])

  useEffect(() => {
    if (!editing) return
    setCategoryPath(editing.parentCategoryId ? [editing.parentCategoryId, editing.categoryId] : [editing.categoryId])
    form.reset({
      title: editing.title,
      description: editing.description,
      budget: editing.budget === null || editing.budget === undefined ? '' : String(Number(editing.budget))
    })
  }, [editing, form])

  const onSubmit = form.handleSubmit(async (values) => {
    if (!categoryPath.length) {
      toast.warning('请选择期望分类')
      return
    }
    const budgetText = (values.budget ?? '').trim()
    const payload: WantPostPublishDTO = {
      title: values.title,
      description: values.description,
      categoryId: categoryPath[categoryPath.length - 1],
      budget: budgetText === '' ? null : Number(budgetText)
    }
    try {
      if (isEdit && editId) {
        await updateWantPost({ id: editId, body: payload }).unwrap()
        toast.success('修改已保存')
        navigate(`/want/${editId}`)
      } else {
        const newId = await publishWantPost(payload).unwrap()
        toast.success('发布成功')
        navigate(`/want/${newId}`)
      }
    } catch (err) {
      if (((err as Error)?.message || '').includes('认证')) navigate('/verify')
    }
  })

  return (
    <div className="page">
      <h2 className="page-title">{isEdit ? '编辑求购' : '发布求购'}</h2>

      <Card className="paper-card max-w-[720px] p-6">
        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1.5">
            <Label>期望分类</Label>
            <CategoryCascader value={categoryPath} onChange={setCategoryPath} />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="title">标题</Label>
            <Input
              id="title"
              maxLength={50}
              placeholder="如：求购二手《数据结构》教材"
              {...form.register('title')}
            />
            {form.formState.errors.title ? (
              <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="budget">心理价</Label>
            <div className="flex items-center gap-3">
              <Input
                id="budget"
                type="number"
                min={0}
                step={1}
                placeholder="留空表示价格面议"
                className="w-[200px]"
                {...form.register('budget')}
              />
              <span className="text-[12px] text-muted-foreground">留空表示价格面议</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">求购描述</Label>
            <Textarea
              id="description"
              rows={5}
              maxLength={500}
              placeholder="说明所需物品的版本、成色要求、期望面交时间地点等"
              {...form.register('description')}
            />
            {form.formState.errors.description ? (
              <p className="text-xs text-destructive">{form.formState.errors.description.message}</p>
            ) : null}
          </div>

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
