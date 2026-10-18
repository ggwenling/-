import { useState } from 'react'
import { Eye, RotateCcw, Search, ShieldCheck, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { StatusBadge } from '@/components/StatusBadge'
import { TableSkeleton } from '@/components/TableSkeleton'
import { EmptyState } from '@/components/EmptyState'
import { DateTimeText } from '@/components/DateTimeText'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  useHandleReportMutation,
  usePageReportsQuery,
  useRejectReportMutation
} from '@/api/reportApi'
import type { ReportVO } from '@/api/types'
import { REPORT_ACTIONS, REPORT_STATUS_MAPS, REPORT_TARGET_MAPS, REPORT_TYPE_MAPS } from '@/lib/constants'
import { cn } from '@/lib/utils'

/** images 可能是 JSON 字符串或数组，做防御式解析 */
function parseImages(images: ReportVO['images']): string[] {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (typeof images === 'string' && images.trim()) {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch {
      return []
    }
  }
  return []
}

export default function ReportWorkbenchPage() {
  const confirm = useConfirm()
  const prompt = usePrompt()

  const [status, setStatus] = useState('ALL')
  const [targetType, setTargetType] = useState('ALL')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const [detail, setDetail] = useState<ReportVO | null>(null)
  const [handling, setHandling] = useState<ReportVO | null>(null)
  const [actions, setActions] = useState<string[]>([])
  const [result, setResult] = useState('')

  const { data, isFetching } = usePageReportsQuery({
    status: status === 'ALL' ? undefined : Number(status),
    targetType: targetType === 'ALL' ? undefined : targetType,
    pageNum,
    pageSize
  })

  const [handleReport, { isLoading: submitting }] = useHandleReportMutation()
  const [rejectReport] = useRejectReportMutation()

  const rows = data?.list ?? []
  const total = data?.total ?? 0

  const openHandle = (row: ReportVO) => {
    setHandling(row)
    setActions([])
    setResult('')
  }

  const toggleAction = (value: string, checked: boolean) => {
    setActions((prev) => (checked ? [...new Set([...prev, value])] : prev.filter((a) => a !== value)))
  }

  const submitHandle = async () => {
    if (!handling) return
    if (!actions.length) {
      toast.warning('请至少选择一项处置动作')
      return
    }
    if (!result.trim()) {
      toast.warning('请填写处置说明')
      return
    }
    try {
      await handleReport({ id: handling.id, actions, result: result.trim() }).unwrap()
      toast.success('工单已处置')
      setHandling(null)
      setDetail(null)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const doReject = async (row: ReportVO) => {
    const ok = await confirm({
      title: '驳回工单',
      description: `确认驳回举报 #${row.id}？驳回后工单将标记为已驳回。`,
      confirmText: '继续',
      tone: 'destructive'
    })
    if (!ok) return
    const reason = await prompt({
      title: '驳回说明',
      label: '驳回理由',
      placeholder: '请填写驳回理由',
      required: true,
      confirmText: '确认驳回',
      tone: 'destructive'
    })
    if (reason === null) return
    try {
      await rejectReport({ id: row.id, result: reason }).unwrap()
      toast.success('已驳回')
      setDetail(null)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="举报工作台"
        description="举报工单核查与组合处置（ADM-04）：下架 / 警告 / 扣分 / 封禁"
        actions={<span className="tape-tag rotate-[-1.5deg]">处置留痕</span>}
      />

      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">工单状态</Label>
            <Select value={status} onValueChange={(v) => { setStatus(v); setPageNum(1) }}>
              <SelectTrigger className="w-[140px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部状态</SelectItem>
                {Object.entries(REPORT_STATUS_MAPS).map(([value, meta]) => (
                  <SelectItem key={value} value={value}>
                    {meta.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">举报对象</Label>
            <Select value={targetType} onValueChange={(v) => { setTargetType(v); setPageNum(1) }}>
              <SelectTrigger className="w-[140px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部对象</SelectItem>
                {Object.entries(REPORT_TARGET_MAPS).map(([value, meta]) => (
                  <SelectItem key={value} value={value}>
                    {meta.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button
            variant="outline"
            onClick={() => {
              setStatus('ALL')
              setTargetType('ALL')
              setPageNum(1)
            }}
          >
            <RotateCcw className="size-4" />
            重置
          </Button>

          <span className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground">
            <Search className="size-3.5" />
            仅后端支持的筛选：状态 / 举报对象
          </span>
        </div>
      </Card>

      <Card className="mt-4 p-4">
        {isFetching && !rows.length ? (
          <TableSkeleton />
        ) : rows.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[72px]">ID</TableHead>
                <TableHead className="w-[170px]">举报对象</TableHead>
                <TableHead className="w-[100px]">举报类型</TableHead>
                <TableHead className="w-[150px]">被举报内容</TableHead>
                <TableHead className="w-[90px]">举报人</TableHead>
                <TableHead className="w-[100px]">状态</TableHead>
                <TableHead className="w-[150px]">提交时间</TableHead>
                <TableHead className="w-[200px] text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const target = REPORT_TARGET_MAPS[row.targetType || ''] ?? { label: row.targetType || '—', badge: 'soft' }
                const type = REPORT_TYPE_MAPS[row.reportType || ''] ?? { label: row.reportType || '—', badge: 'soft' }
                const st = REPORT_STATUS_MAPS[row.status ?? 0] ?? { label: '未知', badge: 'soft' }
                return (
                  <TableRow key={row.id}>
                    <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        <StatusBadge label={target.label} tone={target.badge} />
                        <span className="text-xs text-muted-foreground">#{row.targetId ?? '—'}</span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <StatusBadge label={type.label} tone={type.badge} />
                    </TableCell>
                    <TableCell className="max-w-[180px] truncate" title={row.targetTitle || ''}>
                      {row.targetTitle || '—'}
                    </TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">{row.reporterId ?? '—'}</TableCell>
                    <TableCell>
                      <StatusBadge label={row.statusText || st.label} tone={st.badge} />
                    </TableCell>
                    <TableCell>
                      <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1.5">
                        <Button variant="ghost" size="sm" onClick={() => setDetail(row)}>
                          <Eye className="size-3.5" />
                          详情
                        </Button>
                        {row.status === 0 ? (
                          <>
                            <Button size="sm" onClick={() => openHandle(row)}>
                              <ShieldCheck className="size-3.5" />
                              处置
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => doReject(row)}
                            >
                              <XCircle className="size-3.5" />
                              驳回
                            </Button>
                          </>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="暂无举报工单" description="当前筛选条件下没有匹配的工单" />
        )}

        {total > 0 ? (
          <Pagination
            pageNum={pageNum}
            pageSize={pageSize}
            total={total}
            onPageChange={(next, size) => {
              setPageNum(next)
              setPageSize(size)
            }}
          />
        ) : null}
      </Card>

      {/* 详情抽屉 */}
      <Sheet open={!!detail} onOpenChange={(open) => !open && setDetail(null)}>
        <SheetContent className="w-full sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>举报工单 #{detail?.id}</SheetTitle>
            <SheetDescription>举报详情与证据材料</SheetDescription>
          </SheetHeader>

          {detail ? (
            <div className="flex-1 space-y-4 overflow-y-auto px-6 pb-6">
              <div className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                <Field label="举报对象">
                  {(REPORT_TARGET_MAPS[detail.targetType || '']?.label || detail.targetType || '—') +
                    (detail.targetId != null ? ` #${detail.targetId}` : '')}
                </Field>
                <Field label="举报类型">{REPORT_TYPE_MAPS[detail.reportType || '']?.label || detail.reportType || '—'}</Field>
                <Field label="被举报内容" className="col-span-2">
                  {detail.targetTitle || '—'}
                </Field>
                <Field label="举报人">{detail.reporterId != null ? `用户 #${detail.reporterId}` : '—'}</Field>
                <Field label="状态">{detail.statusText || REPORT_STATUS_MAPS[detail.status ?? 0]?.label || '—'}</Field>
                <Field label="提交时间">{detail.createdAt}</Field>
                <Field label="处置时间">{detail.handledAt || '—'}</Field>
              </div>

              <div className="dashed-card p-3">
                <div className="text-xs text-muted-foreground">举报描述</div>
                <p className="mt-1 text-sm leading-relaxed whitespace-pre-wrap text-foreground">
                  {detail.description || '—'}
                </p>
              </div>

              {parseImages(detail.images).length ? (
                <div>
                  <div className="mb-2 text-xs text-muted-foreground">证据图片</div>
                  <div className="grid grid-cols-3 gap-2">
                    {parseImages(detail.images).map((url, i) => (
                      <a
                        key={i}
                        href={url}
                        target="_blank"
                        rel="noreferrer"
                        className="overflow-hidden rounded-lg border border-border bg-secondary/40"
                      >
                        <img src={url} alt={`证据 ${i + 1}`} className="h-24 w-full object-cover" />
                      </a>
                    ))}
                  </div>
                </div>
              ) : null}

              {detail.result ? (
                <div className="dashed-card p-3">
                  <div className="text-xs text-muted-foreground">处置结果</div>
                  <p className="mt-1 text-sm leading-relaxed whitespace-pre-wrap text-foreground">{detail.result}</p>
                </div>
              ) : null}

              {detail.status === 0 ? (
                <div className="flex gap-2">
                  <Button className="flex-1" onClick={() => openHandle(detail)}>
                    <ShieldCheck className="size-4" />
                    立即处置
                  </Button>
                  <Button variant="outline" className="flex-1" onClick={() => doReject(detail)}>
                    <XCircle className="size-4" />
                    驳回工单
                  </Button>
                </div>
              ) : null}
            </div>
          ) : null}
        </SheetContent>
      </Sheet>

      {/* 处置弹窗 */}
      <Dialog open={!!handling} onOpenChange={(open) => !open && setHandling(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>处置举报工单 #{handling?.id}</DialogTitle>
            <DialogDescription>可多选处置动作，处置说明将写入操作日志与工单结果。</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label className="text-xs text-muted-foreground">处置动作（可组合）</Label>
              <div className="grid grid-cols-2 gap-2">
                {REPORT_ACTIONS.map((item) => {
                  const disabled = item.value === 'TAKE_DOWN' && handling?.targetType === 'USER'
                  return (
                    <label
                      key={item.value}
                      className={cn(
                        'flex cursor-pointer items-center gap-2.5 rounded-lg border border-border bg-card px-3 py-2 text-sm transition-colors',
                        disabled ? 'cursor-not-allowed opacity-50' : 'hover:bg-secondary/60'
                      )}
                    >
                      <Checkbox
                        disabled={disabled}
                        checked={actions.includes(item.value)}
                        onCheckedChange={(checked) => toggleAction(item.value, checked === true)}
                      />
                      <span>{item.label}</span>
                      {disabled ? <span className="ml-auto text-[11px] text-muted-foreground">不适用</span> : null}
                    </label>
                  )
                })}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">处置说明</Label>
              <Textarea
                rows={3}
                value={result}
                placeholder="请填写处置说明（必填）"
                onChange={(e) => setResult(e.target.value)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setHandling(null)}>
              取消
            </Button>
            <Button disabled={submitting} onClick={submitHandle}>
              确认处置
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({ label, value, className, children }: { label: string; value?: string; className?: string; children?: React.ReactNode }) {
  return (
    <div className={className}>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-0.5 break-all text-foreground">{children ?? value ?? '—'}</div>
    </div>
  )
}
