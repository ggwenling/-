import { useState } from 'react'
import { RotateCcw, Search } from 'lucide-react'
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { usePageOperationLogsQuery } from '@/api/logApi'
import { actionBadge, KNOWN_ACTIONS } from '@/lib/constants'

export default function OperationLogPage() {
  const [adminIdInput, setAdminIdInput] = useState('')
  const [adminId, setAdminId] = useState<number | undefined>(undefined)
  const [action, setAction] = useState('ALL')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const { data, isFetching } = usePageOperationLogsQuery({
    adminId,
    action: action === 'ALL' ? undefined : action,
    pageNum,
    pageSize
  })

  const rows = data?.list ?? []
  const total = data?.total ?? 0

  const search = () => {
    const n = Number(adminIdInput)
    setAdminId(adminIdInput.trim() && Number.isFinite(n) ? n : undefined)
    setPageNum(1)
  }

  const reset = () => {
    setAdminIdInput('')
    setAdminId(undefined)
    setAction('ALL')
    setPageNum(1)
  }

  return (
    <div>
      <PageHeader
        title="操作日志"
        description="后台操作审计（ADM-09，三角色共用，只读）；支持按操作人 ID 与动作筛选"
        actions={<span className="stamp-badge">只读审计</span>}
      />

      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">操作人 ID</Label>
            <Input
              value={adminIdInput}
              placeholder="管理员 ID"
              className="w-[160px]"
              onChange={(e) => setAdminIdInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') search()
              }}
            />
          </div>

          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">动作</Label>
            <Select value={action} onValueChange={(v) => { setAction(v); setPageNum(1) }}>
              <SelectTrigger className="w-[230px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="max-h-[320px]">
                <SelectItem value="ALL">全部动作</SelectItem>
                {KNOWN_ACTIONS.map((a) => (
                  <SelectItem key={a} value={a}>
                    {a}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button onClick={search}>
            <Search className="size-4" />
            查询
          </Button>
          <Button variant="outline" onClick={reset}>
            <RotateCcw className="size-4" />
            重置
          </Button>
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
                <TableHead className="w-[170px]">操作人</TableHead>
                <TableHead className="w-[190px]">动作</TableHead>
                <TableHead className="w-[170px]">操作对象</TableHead>
                <TableHead>详情</TableHead>
                <TableHead className="w-[130px]">IP</TableHead>
                <TableHead className="w-[150px]">时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                  <TableCell>
                    <div className="font-medium text-foreground">{row.adminUsername || '—'}</div>
                    <div className="text-[11px] text-muted-foreground">
                      {row.adminId != null ? `#${row.adminId}` : ''}
                    </div>
                  </TableCell>
                  <TableCell>
                    <StatusBadge label={row.action || '—'} tone={actionBadge(row.action)} />
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {row.targetType ? `${row.targetType}${row.targetId != null ? ` #${row.targetId}` : ''}` : '—'}
                  </TableCell>
                  <TableCell className="max-w-[320px] truncate text-muted-foreground" title={row.detail || ''}>
                    {row.detail || '—'}
                  </TableCell>
                  <TableCell className="tabular-nums text-muted-foreground">{row.ip || '—'}</TableCell>
                  <TableCell>
                    <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="暂无操作日志" description="当前筛选条件下没有匹配的日志" />
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
    </div>
  )
}
