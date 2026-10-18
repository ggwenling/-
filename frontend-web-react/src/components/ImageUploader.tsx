import { useRef, useState } from 'react'
import { ImagePlus, Loader2, X } from 'lucide-react'
import { toast } from 'sonner'
import { uploadImage } from '@/api/upload'
import { UPLOAD_ACCEPT } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { GoodsImageDTO } from '@/api/types'

/**
 * 商品图片上传器（替代 Element Plus 的 `el-upload drag`）。
 *
 * 与 Vue 版一致的对外契约：
 * - 上传接口 `POST /uploads`，字段名 `file`，返回 `{url, thumbUrl}`，列表项即该对象
 * - **上限检查在上传成功之后**（Vue 版 `GoodsPublishView.doUpload` 的顺序），
 *   超出时提示「最多 N 张图片」并丢弃该张（行为照抄，见契约表 §6 陷阱 #14）
 * - 首张为封面（列表项显示"封面"标签）
 * - 商品链路**没有**前端 5MB/类型前置校验（与旧版一致，仅靠 `accept` 与后端魔数校验）
 */
export function ImageUploader({
  value,
  onChange,
  max,
  hint,
  beforeUpload,
  onPendingChange,
  maxMessage,
  className
}: {
  value: GoodsImageDTO[]
  onChange: (next: GoodsImageDTO[]) => void
  max: number
  hint?: string
  /**
   * 上传**前置**校验（返回错误文案则拒绝该文件）。
   * 举报链路需要它（MIME + 5MB）；商品发布链路按旧版**不传**该校验（见契约表 §6 陷阱 #14）。
   */
  beforeUpload?: (file: File) => string | null
  /** 上传中数量变化回调（举报弹窗据此拦截提交） */
  onPendingChange?: (pending: number) => void
  /** 超出上限时的提示文案（旧版举报为「最多上传 3 张证据截图」） */
  maxMessage?: string
  className?: string
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  /** 上传中的图片数（对应旧版 `file.status === 'uploading'` 的提交拦截） */
  const [pending, setPending] = useState(0)

  const changePending = (next: number) => {
    setPending(next)
    onPendingChange?.(next)
  }

  const pick = async (files: FileList | null) => {
    if (!files || !files.length) return
    setUploading(true)
    try {
      for (const file of Array.from(files)) {
        if (beforeUpload) {
          const invalid = beforeUpload(file)
          if (invalid) {
            toast.warning(invalid)
            continue
          }
        }
        setPending((n) => n + 1)
        onPendingChange?.(pending + 1)
        try {
          const uploaded = await uploadImage(file)
          // 顺序与 Vue 版一致：先上传，再判断张数上限
          if (value.length >= max) {
            toast.warning(maxMessage || `最多 ${max} 张图片`)
            continue
          }
          value = [...value, { url: uploaded.url, thumbUrl: uploaded.thumbUrl }]
          onChange(value)
        } finally {
          setPending((n) => {
            const next = Math.max(0, n - 1)
            onPendingChange?.(next)
            return next
          })
        }
      }
    } catch {
      /* uploadImage 已按错误码给出提示 */
    } finally {
      setUploading(false)
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className={className} data-pending={pending}>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          void pick(e.dataTransfer.files)
        }}
        className={cn(
          'dashed-card flex w-full flex-col items-center gap-2 px-4 py-8 text-center transition',
          dragOver ? 'border-primary bg-secondary/40' : 'hover:bg-secondary/30'
        )}
      >
        {uploading ? (
          <Loader2 className="size-8 animate-spin text-muted-foreground" />
        ) : (
          <ImagePlus className="size-8 text-muted-foreground" />
        )}
        <span className="text-[13px] text-muted-foreground">{hint || '拖拽或点击上传'}</span>
      </button>

      <input
        ref={inputRef}
        type="file"
        multiple
        accept={UPLOAD_ACCEPT}
        className="hidden"
        onChange={(e) => void pick(e.target.files)}
      />

      {value.length ? (
        <div className="mt-3 grid grid-cols-3 gap-3">
          {value.map((img, idx) => (
            <div key={img.url} className="relative overflow-hidden rounded-lg border border-border bg-secondary/30">
              <img src={img.thumbUrl || img.url} alt="" className="aspect-square w-full object-cover" />
              {idx === 0 ? (
                <span className="absolute top-1 left-1 rounded bg-[var(--success)] px-1.5 py-0.5 text-[10px] font-semibold text-white">
                  封面
                </span>
              ) : null}
              <button
                type="button"
                aria-label="删除图片"
                onClick={() => onChange(value.filter((_, i) => i !== idx))}
                className="absolute top-1 right-1 grid size-5 place-items-center rounded-full bg-destructive text-destructive-foreground transition hover:bg-destructive/90"
              >
                <X className="size-3" />
              </button>
            </div>
          ))}
        </div>
      ) : (
        <p className="mt-3 text-center text-[13px] text-muted-foreground">还没有上传图片</p>
      )}
    </div>
  )
}

export default ImageUploader
