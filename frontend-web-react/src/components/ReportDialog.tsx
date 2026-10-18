import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn } from '@/features/auth/authSlice'
import { useSubmitReportMutation } from '@/api/reportApi'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { ImageUploader } from './ImageUploader'
import { REPORT_IMAGE_MAX, REPORT_TARGET_TEXT, REPORT_TYPE_OPTIONS, UPLOAD_MAX_BYTES, UPLOAD_ACCEPT } from '@/lib/constants'
import type { GoodsImageDTO, ReportTargetType, ReportType } from '@/api/types'

const schema = z.object({
  reportType: z.string().min(1, '请选择举报类型'),
  description: z.string().max(500).optional()
})

type FormValues = z.infer<typeof schema>

/**
 * 举报弹窗（`ReportDialog.vue` 的 React 版，RPT-01）。
 *
 * 与旧版逐条对齐：
 * - 顶部展示举报对象（标题 + 对象类型徽章）
 * - 举报类型必选（提示「请选择举报类型」）；补充描述选填（≤500 字）
 * - 证据图 ≤3 张，**上传前置校验**：MIME 仅 jpg/png/webp（「仅支持 jpg / png / webp 格式图片」）、
 *   单张 >5MB（「单张图片不能超过 5MB」）、超量（「最多上传 3 张证据截图」）
 * - 提交顺序：未登录 → 「请先登录」并跳登录；校验；**上传中拦截**（「证据图片上传中，请稍候」）；
 *   收集 `images`（仅 url，≤3）；成功 → 「举报已提交，可在个人中心查看处理进度」并关闭 + 通知父组件
 * - 关闭即重置表单与已选图片
 */
export function ReportDialog({
  open,
  onOpenChange,
  targetType,
  targetId,
  targetTitle,
  onSubmitted
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  targetType: ReportTargetType
  targetId: number | null
  targetTitle: string
  onSubmitted?: () => void
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const isLoggedIn = useAppSelector(selectIsLoggedIn)

  const [images, setImages] = useState<GoodsImageDTO[]>([])
  const [pending, setPending] = useState(0)
  const [preview, setPreview] = useState<string | null>(null)
  const [submitReport, { isLoading: submitting }] = useSubmitReportMutation()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reportType: '', description: '' }
  })

  useEffect(() => {
    if (!open) {
      form.reset({ reportType: '', description: '' })
      setImages([])
      setPending(0)
      setPreview(null)
    }
  }, [open, form])

  const onSubmit = form.handleSubmit(async (values) => {
    if (!isLoggedIn) {
      toast.warning('请先登录')
      navigate(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    if (targetId == null) return
    if (pending > 0) {
      toast.warning('证据图片上传中，请稍候')
      return
    }
    const urls = images.map((i) => i.url).filter(Boolean).slice(0, REPORT_IMAGE_MAX)
    try {
      await submitReport({
        targetType,
        targetId,
        reportType: values.reportType as ReportType,
        description: values.description?.trim() || undefined,
        images: urls.length ? urls : undefined
      }).unwrap()
      toast.success('举报已提交，可在个人中心查看处理进度')
      onOpenChange(false)
      onSubmitted?.()
    } catch {
      /* 错误提示由 baseQuery 统一给出 */
    }
  })

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <TriangleAlert className="size-4 text-[var(--warning)]" />
              举报
            </DialogTitle>
            <DialogDescription>
              举报对象：{targetTitle || '—'}
              <span className="ml-2 rounded border border-border px-1.5 py-0.5 text-[11px]">
                {REPORT_TARGET_TEXT[targetType] || '内容'}
              </span>
            </DialogDescription>
          </DialogHeader>

          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-1.5">
              <Label>举报类型</Label>
              <Select
                value={form.watch('reportType')}
                onValueChange={(v) => form.setValue('reportType', v, { shouldValidate: true })}
              >
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="请选择举报类型" />
                </SelectTrigger>
                <SelectContent>
                  {REPORT_TYPE_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.reportType ? (
                <p className="text-xs text-destructive">{form.formState.errors.reportType.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5">
              <Label>补充描述（可选）</Label>
              <Textarea rows={3} maxLength={500} placeholder="请补充说明违规情况" {...form.register('description')} />
            </div>

            <div className="space-y-2">
              <Label>证据截图（可选）</Label>
              <ImageUploader
                value={images}
                onChange={setImages}
                max={REPORT_IMAGE_MAX}
                maxMessage="最多上传 3 张证据截图"
                hint="最多 3 张，支持 jpg / png / webp，单张 ≤5MB"
                onPendingChange={setPending}
                beforeUpload={(file) => {
                  if (!UPLOAD_ACCEPT.split(',').includes(file.type)) return '仅支持 jpg / png / webp 格式图片'
                  if (file.size > UPLOAD_MAX_BYTES) return '单张图片不能超过 5MB'
                  return null
                }}
              />
              {images.length ? (
                <div className="flex flex-wrap gap-2">
                  {images.map((img, i) => (
                    <button
                      key={img.url}
                      type="button"
                      className="text-[12px] text-primary hover:underline"
                      onClick={() => setPreview(img.url)}
                    >
                      预览证据 {i + 1}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type="submit" variant="destructive" disabled={submitting}>
                提交举报
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 证据预览（旧版为嵌套 el-dialog「证据预览」） */}
      <Dialog open={preview !== null} onOpenChange={(o) => !o && setPreview(null)}>
        <DialogContent className="sm:max-w-[560px]">
          <DialogHeader>
            <DialogTitle>证据预览</DialogTitle>
          </DialogHeader>
          {preview ? <img src={preview} alt="证据" className="max-h-[60vh] w-full rounded-lg object-contain" /> : null}
        </DialogContent>
      </Dialog>
    </>
  )
}

export default ReportDialog
