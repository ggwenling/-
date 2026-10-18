import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'

interface PromptOptions {
  title: string
  description?: ReactNode
  label?: string
  placeholder?: string
  defaultValue?: string
  required?: boolean
  confirmText?: string
  tone?: 'default' | 'destructive'
}

type PromptFn = (options: PromptOptions) => Promise<string | null>

const PromptContext = createContext<PromptFn | null>(null)

/**
 * 命令式文本输入弹窗：替代旧版 ElMessageBox.prompt。
 * 用于下架/删除原因、封禁理由等；确认返回文本，取消返回 null。
 */
export function PromptProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [options, setOptions] = useState<PromptOptions | null>(null)
  const [value, setValue] = useState('')
  const resolverRef = useRef<((v: string | null) => void) | null>(null)

  const prompt = useCallback<PromptFn>((opts) => {
    setOptions(opts)
    setValue(opts.defaultValue || '')
    setOpen(true)
    return new Promise<string | null>((resolve) => {
      resolverRef.current = resolve
    })
  }, [])

  const settle = (result: string | null) => {
    resolverRef.current?.(result)
    resolverRef.current = null
    setOpen(false)
  }

  const handleConfirm = () => {
    const text = value.trim()
    if (options?.required && !text) {
      toast.warning('请填写内容后再提交')
      return
    }
    settle(text)
  }

  return (
    <PromptContext.Provider value={prompt}>
      {children}
      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (!next) settle(null)
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{options?.title}</DialogTitle>
            {options?.description ? <DialogDescription>{options.description}</DialogDescription> : null}
          </DialogHeader>

          <div className="space-y-1.5">
            {options?.label ? <Label>{options.label}</Label> : null}
            <Textarea
              autoFocus
              rows={3}
              value={value}
              placeholder={options?.placeholder}
              onChange={(e) => setValue(e.target.value)}
            />
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => settle(null)}>
              取消
            </Button>
            <Button
              className={cn(options?.tone === 'destructive' && 'bg-destructive text-destructive-foreground hover:bg-destructive/90')}
              onClick={handleConfirm}
            >
              {options?.confirmText || '确认'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PromptContext.Provider>
  )
}

export function usePrompt(): PromptFn {
  const ctx = useContext(PromptContext)
  if (!ctx) throw new Error('usePrompt 必须在 PromptProvider 内使用')
  return ctx
}
