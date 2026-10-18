import { useState } from 'react'
import { Eye, RotateCcw, Search, Trash2, Undo2 } from 'lucide-react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  useDeleteContentMutation,
  usePageContentsQuery,
  useRestoreContentMutation,
  useTakeDownContentMutation
} from '@/api/contentApi'
import type { AdminContentVO } from '@/api/types'
import { CONTENT_STATUS_MAPS, contentStatusMeta } from '@/lib/constants'
import { formatAmount } from '@/lib/format'

type TargetType = 'GOODS' | 'WANT' | 'SWAP'

const TARGET_TABS: { value: TargetType; label: string }[] = [
  { value: 'GOODS', label: '商品' },
  { value: 'WANT', label: '求购' },
  { value: 'SWAP', label: '交换' }
]

/** 下架：GOODS 在售 / WANT|SWAP 开放中 */
const canTakeDown = (row: AdminContentVO) =>
  row.targetType === 'GOODS' ? row.status === 'ON_SALE' : row.status === 'OPEN'

/** 恢复：GOODS 已下架 / WANT|SWAP 已关闭 */
const canRestore = (row: AdminContentVO) =>
  row.targetType === 'GOODS' ? row.status === 'OFF_SALE' : row.status === 'CLOSED'

/** 删除：交易中的商品与已删除内容不可删除 */
const canDelete = (row: AdminContentVO) => {
  if (row.status === 'DELETED') return false
  if (row.targetType === 'GOODS' && row.status === 'IN_TRANSACTION') return false
  return true
}

export default function ContentManagePage() {
  const confirm = useConfirm()
  const prompt = usePrompt()

  const [targetType, setTargetType] = useState<TargetType>('GOODS')
  const [status, setStatus] = useState('ALL')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [detail, setDetail] = useState<AdminContentVO | null>(null)

  const { data, isFetching } = usePageContentsQuery({
    targetType,
    status: status === 'ALL' ? undefined : status,
    keyword: keyword || undefined,
    pageNum,
    pageSize
  })

  const [takeDown] = useTakeDownContentMutation()
  const [restore] = useRestoreContentMutation()
  const [deleteContent] = useDeleteContentMutation()

  const rows = data?.list ?? []
  const total = data?.total ?? 0
  const statusOptions = Object.entries(CONTENT_STATUS_MAPS[targetType] || {})

  const switchTarget = (next: string) => {
    setTargetType(next as TargetType)
    setStatus('ALL')
    setPageNum(1)
  }

  const search = () => {
    setKeyword(keywordInput.trim())
    setPageNum(1)
  }

  const reset = () => {
    setKeywordInput('')
    setKeyword('')
    setStatus('ALL')
    setPageNum(1)
  }

  const onTakeDown = async (row: AdminContentVO) => {
    const reason = await prompt({
      title: '下架内容',
      description: `${row.targetType} #${row.id} ${row.title || ''}`,
      label: '下架原因',
      placeholder: '请填写下架原因（将展示给发布者）',
      required: true,
      confirmText: '确认下架',
      tone: 'destructive'
    })
    if (reason === null) return
    try {
      await takeDown({ targetType: row.targetType, id: row.id, reason }).unwrap()
      toast.success('已下架')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onRestore = async (row: AdminContentVO) => {
    const ok = await confirm({
      title: '恢复内容',
      description: `确认恢复 ${row.targetType} #${row.id}「${row.title || '—'}」？恢复后将重新对外可见。`,
      confirmText: '确认恢复'
    })
    if (!ok) return
    try {
      await restore({ targetType: row.targetType, id: row.id }).unwrap()
      toast.success('已恢复')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onDelete = async (row: AdminContentVO) => {
    if (!canDelete(row)) {
      toast.warning(row.status === 'DELETED' ? '该内容已删除' : '交易中的商品不可删除')
      return
    }
    const reason = await prompt({
      title: '删除内容',
      description: '删除为软删除，不可在列表内恢复。',
      label: '删除原因',
      placeholder: '请填写删除原因',
      required: true,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (reason === null) return
    try {
      await deleteContent({ targetType: row.targetType, id: row.id, reason }).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="内容管理"
        description="商品 / 求购 / 交换三类内容统一巡查与处置（ADM-02）"
        actions={<span className="tape-tag rotate-[-1.5deg]">软删除可追溯</span>}
      />

      <Card className="p-4">
        <Tabs value={targetType} onValueChange={switchTarget}>
          <TabsList>
            {TARGET_TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value}>
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <div className="mt-4 flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">状态</Label>
            <Select value={status} onValueChange={(v) => { setStatus(v); setPageNum(1) }}>
              <SelectTrigger className="w-[150px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部状态</SelectItem>
                {statusOptions.map(([value, meta]) => (
                  <SelectItem key={value} value={value}>
                    {meta.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">关键词</Label>
            <Input
              value={keywordInput}
              placeholder="标题关键词"
              className="w-[220px]"
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') search()
              }}
            />
          </div>

          <div className="flex items-center gap-2">
            <Button onClick={search}>
              <Search className="size-4" />
              查询
            </Button>
            <Button variant="outline" onClick={reset}>
              <RotateCcw className="size-4" />
              重置
            </Button>
          </div>
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
                <TableHead>标题</TableHead>
                <TableHead className="w-[90px]">发布人</TableHead>
                <TableHead className="w-[110px]">金额</TableHead>
                <TableHead className="w-[100px]">状态</TableHead>
                <TableHead className="w-[150px]">创建时间</TableHead>
                <TableHead className="w-[240px] text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const meta = contentStatusMeta(row.targetType, row.status)
                return (
                  <TableRow key={`${row.targetType}-${row.id}`}>
                    <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                    <TableCell className="max-w-[280px] truncate font-medium" title={row.title || ''}>
                      {row.title || '—'}
                    </TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">{row.ownerId ?? '—'}</TableCell>
                    <TableCell className="tabular-nums">{formatAmount(row.amount)}</TableCell>
                    <TableCell>
                      <StatusBadge label={meta.label} tone={meta.badge} />
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
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!canTakeDown(row)}
                          onClick={() => onTakeDown(row)}
                        >
                          <Undo2 className="size-3.5" />
                          下架
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!canRestore(row)}
                          onClick={() => onRestore(row)}
                        >
                          <RotateCcw className="size-3.5" />
                          恢复
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          disabled={!canDelete(row)}
                          onClick={() => onDelete(row)}
                        >
                          <Trash2 className="size-3.5" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="暂无内容" description="当前筛选条件下没有匹配的内容记录" />
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

      {/* 详情 */}
      <Dialog open={!!detail} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>内容详情</DialogTitle>
            <DialogDescription>
              {detail ? `${detail.targetType} #${detail.id}` : ''}
            </DialogDescription>
          </DialogHeader>

          {detail ? (
            <div className="space-y-3 text-sm">
              <div className="grid grid-cols-2 gap-x-4 gap-y-3">
                <Field label="类型" value={detail.targetType} />
                <Field label="ID" value={`#${detail.id}`} />
                <Field label="标题" value={detail.title || '—'} className="col-span-2" />
                <Field label="发布人" value={detail.ownerId != null ? `用户 #${detail.ownerId}` : '—'} />
                <Field label="金额" value={formatAmount(detail.amount)} />
                <Field label="状态" value={contentStatusMeta(detail.targetType, detail.status).label} />
                <Field label="创建时间" value={detail.createdAt} />
              </div>

              {(detail.offSaleReason || detail.deleteReason || detail.deletedAt) && (
                <div className="dashed-card space-y-2 p-3">
                  {detail.offSaleReason ? <Field label="下架原因" value={detail.offSaleReason} /> : null}
                  {detail.deleteReason ? <Field label="删除原因" value={detail.deleteReason} /> : null}
                  {detail.deletedAt ? <Field label="删除时间" value={detail.deletedAt} /> : null}
                </div>
              )}
            </div>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Field({ label, value, className }: { label: string; value?: string; className?: string }) {
  return (
    <div className={className}>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-0.5 break-all text-foreground">{value || '—'}</div>
    </div>
  )
}
