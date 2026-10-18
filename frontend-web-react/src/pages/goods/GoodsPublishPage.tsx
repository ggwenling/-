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
  useGetGoodsQuery,
  useListCategoriesQuery,
  useListTagsQuery,
  usePublishGoodsMutation,
  useUpdateGoodsMutation
} from '@/api/goodsApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { CategoryCascader } from '@/components/CategoryCascader'
import { ImageUploader } from '@/components/ImageUploader'
import { CONDITION_TEXT, GOODS_IMAGE_MAX, GOODS_TAG_MAX, TEXTBOOK_CATEGORY_ID } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { GoodsImageDTO, GoodsPublishDTO } from '@/api/types'

/** 校验规则与提示文案与 Vue 版 GoodsPublishView 的 el-form rules 逐字一致 */
const schema = z.object({
  title: z.string().min(1, '请输入标题').max(50),
  description: z.string().min(1, '请输入描述').max(500),
  price: z.coerce.number({ message: '请填写价格' }).min(0),
  conditionLevel: z.number().min(1).max(4),
  tradeLocation: z.string().max(100).optional(),
  courseName: z.string().max(100).optional(),
  isbn: z.string().max(20).optional()
})

type FormValues = z.infer<typeof schema>

/**
 * 发布/编辑商品（PRD GDS-01/02、§5.2，编辑复用路由 + `?id=`）。
 *
 * 与 Vue 版逐条对齐：
 * - 左侧表单：分类（二级级联，提交取末级 id）/标题(≤50)/标签(≤5)/成色(1~4，默认 2)/价格(≥0，精度 2，
 *   "0 元 = 免费赠送")/交易地点(≤100)/描述(≤500)/**教材动态字段**（一级分类 id=1 时出现）
 * - 右侧图片：1~9 张，首张封面；**上限检查在上传之后**、无 5MB 前置校验（照抄旧版，见契约表陷阱 #14）
 * - 提交前 `images.length < 1` → 「请至少上传 1 张商品图片」
 * - 成功：编辑 → 「修改已保存」并跳详情；新建 → 「发布成功」并跳新商品详情
 * - 失败文案含「认证」→ 跳 `/verify`（后端 AUTH_NOT_CERTIFIED / ACCOUNT_RESTRICTED）
 */
export default function GoodsPublishPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)

  const editId = Number(searchParams.get('id')) || null
  const isEdit = editId !== null

  const [categoryPath, setCategoryPath] = useState<number[]>([])
  const [tagIds, setTagIds] = useState<number[]>([])
  const [images, setImages] = useState<GoodsImageDTO[]>([])
  const [editParentCategoryId, setEditParentCategoryId] = useState<number | null>(null)

  const { data: categories = [] } = useListCategoriesQuery()
  const { data: tags = [] } = useListTagsQuery()
  const { data: editing } = useGetGoodsQuery(editId as number, { skip: !isEdit })
  const [publishGoods, { isLoading: publishing }] = usePublishGoodsMutation()
  const [updateGoods, { isLoading: updating }] = useUpdateGoodsMutation()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: '',
      description: '',
      price: 0,
      conditionLevel: 2,
      tradeLocation: '',
      courseName: '',
      isbn: ''
    }
  })

  // 未登录兜底（路由守卫之外的二次校验，与旧版 onMounted 行为一致）
  useEffect(() => {
    if (!isLoggedIn) {
      navigate(`/login?redirect=${encodeURIComponent(isEdit ? `/publish?id=${editId}` : '/publish')}`, { replace: true })
    }
  }, [isLoggedIn, isEdit, editId, navigate])

  // 编辑回填
  useEffect(() => {
    if (!editing) return
    setCategoryPath(editing.parentCategoryId ? [editing.parentCategoryId, editing.categoryId] : [editing.categoryId])
    setEditParentCategoryId(editing.parentCategoryId ?? null)
    setTagIds((editing.tags ?? []).map((t) => t.id))
    setImages((editing.images ?? []).map((i) => ({ url: i.url, thumbUrl: i.thumbUrl || i.url })))
    form.reset({
      title: editing.title,
      description: editing.description,
      price: Number(editing.price),
      conditionLevel: (editing.conditionLevel ?? 2) as 1 | 2 | 3 | 4,
      tradeLocation: editing.tradeLocation || '',
      courseName: editing.courseName || '',
      isbn: editing.isbn || ''
    })
  }, [editing, form])

  /** 教材分区（一级分类 id=1，PRD §12.1）：与旧版同口径 */
  const isTextbook = categoryPath.length === 2 ? categoryPath[0] === TEXTBOOK_CATEGORY_ID : editParentCategoryId === TEXTBOOK_CATEGORY_ID

  const categoryOptions = categories

  const onSubmit = form.handleSubmit(async (values) => {
    if (!categoryPath.length) {
      toast.warning('请选择分类')
      return
    }
    if (images.length < 1) {
      toast.warning('请至少上传 1 张商品图片')
      return
    }
    const payload: GoodsPublishDTO = {
      title: values.title,
      description: values.description,
      categoryId: categoryPath[categoryPath.length - 1],
      conditionLevel: values.conditionLevel as 1 | 2 | 3 | 4,
      price: values.price ?? 0,
      tradeLocation: values.tradeLocation || undefined,
      tagIds,
      images
    }
    if (isTextbook) {
      payload.courseName = values.courseName || undefined
      payload.isbn = values.isbn || undefined
    }
    try {
      if (isEdit && editId) {
        await updateGoods({ id: editId, body: payload }).unwrap()
        toast.success('修改已保存')
        navigate(`/goods/${editId}`)
      } else {
        const newId = await publishGoods(payload).unwrap()
        toast.success('发布成功')
        navigate(`/goods/${newId}`)
      }
    } catch (err) {
      const msg = (err as Error)?.message || ''
      if (msg.includes('认证')) navigate('/verify')
    }
  })

  return (
    <div className="page">
      <h2 className="page-title">{isEdit ? '编辑商品' : '发布商品'}</h2>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_360px]">
        {/* 左：表单 */}
        <Card className="paper-card p-6">
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-1.5">
              <Label>分类</Label>
              <CategoryCascader value={categoryPath} onChange={setCategoryPath} />
              {!categoryOptions.length ? <p className="text-xs text-muted-foreground">分类加载中…</p> : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="title">标题</Label>
              <Input id="title" maxLength={50} placeholder="品牌/型号/成色一眼可读" {...form.register('title')} />
              {form.formState.errors.title ? (
                <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label>标签</Label>
              <div className="flex flex-wrap gap-2">
                {tags.map((t) => {
                  const picked = tagIds.includes(t.id)
                  return (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() =>
                        setTagIds((prev) => {
                          if (prev.includes(t.id)) return prev.filter((x) => x !== t.id)
                          if (prev.length >= GOODS_TAG_MAX) {
                            toast.warning(`最多选 ${GOODS_TAG_MAX} 个标签`)
                            return prev
                          }
                          return [...prev, t.id]
                        })
                      }
                      className={cn(
                        'rounded-full border px-3 py-1 text-[12px] transition',
                        picked
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-border bg-card text-muted-foreground hover:bg-secondary/60'
                      )}
                    >
                      {t.name}
                    </button>
                  )
                })}
                {!tags.length ? <span className="text-xs text-muted-foreground">暂无可选标签</span> : null}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label>成色</Label>
              <div className="flex flex-wrap gap-2">
                {Object.entries(CONDITION_TEXT).map(([level, text]) => {
                  const value = Number(level)
                  const picked = form.watch('conditionLevel') === value
                  return (
                    <button
                      key={level}
                      type="button"
                      onClick={() => form.setValue('conditionLevel', value, { shouldValidate: true })}
                      className={cn(
                        'rounded-lg border px-3 py-1.5 text-[13px] transition',
                        picked
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-border bg-card text-muted-foreground hover:bg-secondary/60'
                      )}
                    >
                      {text}
                    </button>
                  )
                })}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="price">价格</Label>
              <div className="flex items-center gap-3">
                <Input
                  id="price"
                  type="number"
                  min={0}
                  step={1}
                  className="w-[200px]"
                  {...form.register('price')}
                />
                <span className="text-[13px] text-muted-foreground">0 元 = 免费赠送</span>
              </div>
              {form.formState.errors.price ? (
                <p className="text-xs text-destructive">{form.formState.errors.price.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="tradeLocation">交易地点</Label>
              <Input
                id="tradeLocation"
                maxLength={100}
                placeholder="如：东门快递点 / 图书馆门口"
                {...form.register('tradeLocation')}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="description">描述</Label>
              <Textarea
                id="description"
                rows={5}
                maxLength={500}
                placeholder="购买渠道、使用时长、瑕疵说明等"
                {...form.register('description')}
              />
              {form.formState.errors.description ? (
                <p className="text-xs text-destructive">{form.formState.errors.description.message}</p>
              ) : null}
            </div>

            {isTextbook ? (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="courseName">课程名</Label>
                  <Input
                    id="courseName"
                    maxLength={100}
                    placeholder="如：高等数学（教材专区检索用）"
                    {...form.register('courseName')}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="isbn">ISBN</Label>
                  <Input id="isbn" maxLength={20} placeholder="教材 ISBN" {...form.register('isbn')} />
                </div>
              </>
            ) : null}

            <Button type="submit" size="lg" disabled={publishing || updating}>
              {publishing || updating ? <Loader2 className="size-4 animate-spin" /> : null}
              {isEdit ? '保存修改' : '发 布'}
            </Button>
          </form>
        </Card>

        {/* 右：图片上传 */}
        <Card className="paper-card h-fit p-5">
          <div className="flex items-center justify-between gap-2">
            <b className="text-[15px] text-foreground">商品图片（1~9 张，首张为封面）</b>
            <Badge variant="soft">{images.length}/{GOODS_IMAGE_MAX}</Badge>
          </div>
          <div className="mt-4">
            <ImageUploader
              value={images}
              onChange={setImages}
              max={GOODS_IMAGE_MAX}
              hint="拖拽或点击上传，单张 ≤5MB"
            />
          </div>
        </Card>
      </div>
    </div>
  )
}
