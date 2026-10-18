import { useState } from 'react'
import { Eye, RotateCcw, Search, ShieldOff, Sliders, UserCheck } from 'lucide-react'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import { useAppSelector } from '@/app/hooks'
import { selectRole } from '@/features/auth/authSlice'
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
  useAdjustCreditMutation,
  useBanUserMutation,
  useGetUserDetailQuery,
  usePageUserCreditsQuery,
  usePageUsersQuery,
  useUnbanUserMutation
} from '@/api/userApi'
import type { AdminUserVO } from '@/api/types'
import { CREDIT_REASON_LABELS, creditTone } from '@/lib/constants'

/** 后端 User.authStatus：0=未认证 / 1=已认证（AUTH_STATUS_UNVERIFIED / VERIFIED） */
const AUTH_STATUS: Record<number, { label: string; tone: string }> = {
  0: { label: '未认证', tone: 'soft' },
  1: { label: '已认证', tone: 'success' }
}

/** 后端 User.status：0=正常 / 1=已封禁（STATUS_NORMAL / STATUS_BANNED） */
const USER_STATUS_NORMAL = 0
const USER_STATUS_BANNED = 1

const CREDIT_TONE: Record<string, string> = {
  danger: 'destructive',
  warning: 'warning',
  primary: 'default',
  success: 'success',
  muted: 'soft'
}

const DURATION_OPTIONS = [
  { value: '7', label: '封禁 7 天' },
  { value: '30', label: '封禁 30 天' },
  { value: '90', label: '封禁 90 天' },
  { value: 'PERMANENT', label: '永久封禁' }
]

export default function UserManagePage() {
  const confirm = useConfirm()
  const prompt = usePrompt()
  const role = useAppSelector(selectRole)
  const canAdjustCredit = role === 'super'

  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [detailId, setDetailId] = useState<number | null>(null)

  const [banTarget, setBanTarget] = useState<AdminUserVO | null>(null)
  const [banReason, setBanReason] = useState('')
  const [banDuration, setBanDuration] = useState('30')

  const [creditTarget, setCreditTarget] = useState<AdminUserVO | null>(null)
  const [creditChange, setCreditChange] = useState('')
  const [creditRemark, setCreditRemark] = useState('')

  const { data, isFetching } = usePageUsersQuery({
    username: keyword || undefined,
    pageNum,
    pageSize
  })
  const [unbanUser] = useUnbanUserMutation()
  const [banUser, { isLoading: banning }] = useBanUserMutation()
  const [adjustCredit, { isLoading: adjusting }] = useAdjustCreditMutation()

  const rows = data?.list ?? []
  const total = data?.total ?? 0

  const onUnban = async (row: AdminUserVO) => {
    const ok = await confirm({
      title: '解除封禁',
      description: `确认解封用户「${row.nickname || row.username}」？解封后该用户可正常登录与交易。`,
      confirmText: '确认解封'
    })
    if (!ok) return
    try {
      await unbanUser(row.id).unwrap()
      toast.success('已解封')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const submitBan = async () => {
    if (!banTarget) return
    if (!banReason.trim()) {
      toast.warning('请填写封禁理由')
      return
    }
    try {
      await banUser({
        id: banTarget.id,
        reason: banReason.trim(),
        durationDays: banDuration === 'PERMANENT' ? null : Number(banDuration)
      }).unwrap()
      toast.success('已封禁')
      setBanTarget(null)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const submitCredit = async () => {
    if (!creditTarget) return
    const change = Number(creditChange)
    if (!Number.isFinite(change) || change === 0) {
      toast.warning('请输入非 0 的信用分变动值')
      return
    }
    if (!creditRemark.trim()) {
      toast.warning('请填写调整说明')
      return
    }
    try {
      await adjustCredit({ id: creditTarget.id, change, remark: creditRemark.trim() }).unwrap()
      toast.success('信用分已调整')
      setCreditTarget(null)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="用户管理"
        description="用户检索、封禁/解封与信用分调整（ADM-05）"
        actions={
          canAdjustCredit ? (
            <span className="tape-tag rotate-[-1.5deg]">信用调整：超级管理员专属</span>
          ) : (
            <span className="stamp-badge">当前角色只读信用调整</span>
          )
        }
      />

      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">用户名</Label>
            <Input
              value={keywordInput}
              placeholder="按用户名搜索（支持模糊匹配）"
              className="w-[220px]"
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setKeyword(keywordInput.trim())
                  setPageNum(1)
                }
              }}
            />
          </div>
          <Button
            onClick={() => {
              setKeyword(keywordInput.trim())
              setPageNum(1)
            }}
          >
            <Search className="size-4" />
            查询
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setKeywordInput('')
              setKeyword('')
              setPageNum(1)
            }}
          >
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
                <TableHead className="w-[150px]">用户名</TableHead>
                <TableHead className="w-[140px]">昵称</TableHead>
                <TableHead className="w-[140px]">学院</TableHead>
                <TableHead className="w-[100px]">信用分</TableHead>
                <TableHead className="w-[100px]">认证</TableHead>
                <TableHead className="w-[120px]">状态</TableHead>
                <TableHead className="w-[150px]">注册时间</TableHead>
                <TableHead className="w-[230px] text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const banned = row.status === USER_STATUS_BANNED
                const auth = AUTH_STATUS[row.authStatus ?? -1] ?? { label: '—', tone: 'soft' }
                return (
                  <TableRow key={row.id}>
                    <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                    <TableCell className="font-medium">{row.username}</TableCell>
                    <TableCell className="max-w-[140px] truncate">{row.nickname || '—'}</TableCell>
                    <TableCell className="max-w-[140px] truncate text-muted-foreground">{row.college || '—'}</TableCell>
                    <TableCell>
                      <StatusBadge
                        label={row.creditScore != null ? String(row.creditScore) : '—'}
                        tone={CREDIT_TONE[creditTone(row.creditScore)]}
                      />
                    </TableCell>
                    <TableCell>
                      <StatusBadge label={auth.label} tone={auth.tone} />
                    </TableCell>
                    <TableCell>
                      {banned ? <StatusBadge label="已封禁" tone="destructive" /> : <StatusBadge label="正常" tone="success" />}
                    </TableCell>
                    <TableCell>
                      <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1.5">
                        <Button variant="ghost" size="sm" onClick={() => setDetailId(row.id)}>
                          <Eye className="size-3.5" />
                          详情
                        </Button>
                        {banned ? (
                          <Button variant="outline" size="sm" onClick={() => onUnban(row)}>
                            <UserCheck className="size-3.5" />
                            解封
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => {
                              setBanTarget(row)
                              setBanReason('')
                              setBanDuration('30')
                            }}
                          >
                            <ShieldOff className="size-3.5" />
                            封禁
                          </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!canAdjustCredit}
                          title={canAdjustCredit ? '' : '仅超级管理员可调整信用分'}
                          onClick={() => {
                            setCreditTarget(row)
                            setCreditChange('')
                            setCreditRemark('')
                          }}
                        >
                          <Sliders className="size-3.5" />
                          信用
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="暂无用户" description="当前筛选条件下没有匹配的用户" />
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

      {/* 用户详情 + 信用流水 */}
      <UserDetailSheet
        userId={detailId}
        canAdjustCredit={canAdjustCredit}
        onClose={() => setDetailId(null)}
        onAdjust={(user) => {
          setDetailId(null)
          setCreditTarget(user)
          setCreditChange('')
          setCreditRemark('')
        }}
      />

      {/* 封禁弹窗 */}
      <Dialog open={!!banTarget} onOpenChange={(open) => !open && setBanTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>封禁用户</DialogTitle>
            <DialogDescription>
              {banTarget ? `${banTarget.nickname || banTarget.username}（#${banTarget.id}）` : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">封禁时长</Label>
              <Select value={banDuration} onValueChange={setBanDuration}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DURATION_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">封禁理由</Label>
              <Textarea
                rows={3}
                value={banReason}
                placeholder="请填写封禁理由（必填）"
                onChange={(e) => setBanReason(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBanTarget(null)}>
              取消
            </Button>
            <Button
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={banning}
              onClick={submitBan}
            >
              确认封禁
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 信用调整弹窗 */}
      <Dialog open={!!creditTarget} onOpenChange={(open) => !open && setCreditTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>调整信用分</DialogTitle>
            <DialogDescription>
              {creditTarget
                ? `${creditTarget.nickname || creditTarget.username}（当前 ${creditTarget.creditScore ?? '—'} 分）`
                : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">变动值（正数加分 / 负数扣分）</Label>
              <Input
                type="number"
                value={creditChange}
                placeholder="例如 10 或 -5"
                onChange={(e) => setCreditChange(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">调整说明</Label>
              <Textarea
                rows={3}
                value={creditRemark}
                placeholder="请填写调整说明（必填）"
                onChange={(e) => setCreditRemark(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreditTarget(null)}>
              取消
            </Button>
            <Button disabled={adjusting} onClick={submitCredit}>
              确认调整
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function UserDetailSheet({
  userId,
  canAdjustCredit,
  onClose,
  onAdjust
}: {
  userId: number | null
  canAdjustCredit: boolean
  onClose: () => void
  onAdjust: (user: AdminUserVO) => void
}) {
  const [creditPage, setCreditPage] = useState(1)
  const creditPageSize = 10

  const { data: user, isFetching } = useGetUserDetailQuery(userId as number, { skip: userId == null })
  const { data: credits, isFetching: creditLoading } = usePageUserCreditsQuery(
    { id: userId as number, pageNum: creditPage, pageSize: creditPageSize },
    { skip: userId == null }
  )

  const logs = credits?.list ?? []

  return (
    <Sheet
      open={userId != null}
      onOpenChange={(open) => {
        if (!open) {
          setCreditPage(1)
          onClose()
        }
      }}
    >
      <SheetContent className="w-full sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>用户详情 {user ? `#${user.id}` : ''}</SheetTitle>
          <SheetDescription>基础资料与信用流水</SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-4 overflow-y-auto px-6 pb-6">
          {isFetching && !user ? (
            <TableSkeleton rows={4} />
          ) : user ? (
            <>
              <div className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                <Field label="用户名">{user.username}</Field>
                <Field label="昵称">{user.nickname || '—'}</Field>
                <Field label="学院">{user.college || '—'}</Field>
                <Field label="信用分">{user.creditScore != null ? String(user.creditScore) : '—'}</Field>
                <Field label="认证状态">{AUTH_STATUS[user.authStatus ?? -1]?.label || '—'}</Field>
                <Field label="账号状态">{user.status === USER_STATUS_BANNED ? '已封禁' : '正常'}</Field>
                <Field label="注册时间">{user.createdAt || '—'}</Field>
                <Field label="最近登录">{user.lastLoginAt || '—'}</Field>
                {user.bio ? <Field label="简介" className="col-span-2">{user.bio}</Field> : null}
                {user.banReason ? <Field label="封禁理由" className="col-span-2">{user.banReason}</Field> : null}
                {user.bannedUntil ? <Field label="封禁至" className="col-span-2">{user.bannedUntil}</Field> : null}
              </div>

              {canAdjustCredit ? (
                <Button variant="outline" className="w-full" onClick={() => onAdjust(user)}>
                  <Sliders className="size-4" />
                  调整信用分
                </Button>
              ) : null}

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium">信用流水</span>
                  <span className="text-xs text-muted-foreground">共 {credits?.total ?? 0} 条</span>
                </div>

                {creditLoading && !logs.length ? (
                  <TableSkeleton rows={4} />
                ) : logs.length ? (
                  <div className="space-y-2">
                    {logs.map((log) => (
                      <div key={log.id} className="dashed-card flex items-start justify-between gap-3 p-3">
                        <div className="min-w-0">
                          <div className="text-sm font-medium text-foreground">
                            {CREDIT_REASON_LABELS[log.reason || ''] || log.reason || '—'}
                          </div>
                          <div className="mt-0.5 truncate text-xs text-muted-foreground">{log.remark || '—'}</div>
                          <div className="mt-0.5 text-[11px] text-muted-foreground">
                            <DateTimeText value={log.createdAt} />
                          </div>
                        </div>
                        <span
                          className={
                            'shrink-0 text-sm font-bold tabular-nums ' +
                            (Number(log.scoreChange) >= 0 ? 'text-[var(--success)]' : 'text-destructive')
                          }
                        >
                          {Number(log.scoreChange) >= 0 ? '+' : ''}
                          {log.scoreChange}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <EmptyState title="暂无信用流水" />
                )}

                {(credits?.total ?? 0) > creditPageSize ? (
                  <Pagination
                    pageNum={creditPage}
                    pageSize={creditPageSize}
                    total={credits?.total ?? 0}
                    pageSizeOptions={[10, 20, 50]}
                    onPageChange={(next) => setCreditPage(next)}
                  />
                ) : null}
              </div>
            </>
          ) : (
            <EmptyState title="用户不存在" />
          )}
        </div>
      </SheetContent>
    </Sheet>
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
