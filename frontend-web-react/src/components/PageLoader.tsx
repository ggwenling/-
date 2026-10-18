import { Loader2 } from 'lucide-react'

/** 路由级懒加载占位（避免白屏） */
export function PageLoader() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center gap-3 text-muted-foreground">
      <Loader2 className="size-5 animate-spin text-primary" />
      <span className="text-sm">加载中…</span>
    </div>
  )
}
