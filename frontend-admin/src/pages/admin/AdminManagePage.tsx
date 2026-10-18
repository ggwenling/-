import { useState } from 'react'
import { Ban, CheckCircle2, KeyRound, Pencil, RotateCcw, Search, UserPlus } from 'lucide-react'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  useCreateAccountMutation,
  usePageAccountsQuery,
  useResetAccountPasswordMutation,
  useUpdateAccountMutation
} from '@/api/accountApi'
import type { AdminRole, AdminVO } from '@/api/types'
import { ALL_ROLES, ROLE_META } from '@/lib/constants'

interface EditState {
  id?: number
  username: string
  password: string
  realName: string
  role: AdminRole
  status: number
}

/** 后端 Admin.status：0=启用(STATUS_ENABLED) / 1=禁用(STATUS_DISABLED) */
const ADMIN_STATUS_ENABLED = 0
const ADMIN_STATUS_DISABLED = 1

const emptyForm: EditState = { username: '', password: '', realName: '', role: 'auditor', status: ADMIN_STATUS_ENABLED }

export default function AdminManagePage() {
  const confirm = useConfirm()

  const [adminIdInput, setAdminIdInput] = useState('')
  const [adminId, setAdminId] = useState<number | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [form, setForm] = useState<EditState>(emptyForm)
  const [pwdTarget, setPwdTarget] = useState<AdminVO | null>(null)
  const [newPassword, setNewPassword] = useState('')

  const { data, isFetching } = usePageAccountsQuery({ adminId, pageNum, pageSize })
  const [createAccount, { isLoading: creating }] = useCreateAccountMutation()
  const [updateAccount, { isLoading: updating }] = useUpdateAccountMutation()
  const [resetPassword, { isLoading: resetting }] = useResetAccountPasswordMutation()

  const rows = data?.list ?? []
  const total = data?.total ?? 0
  const isEdit = form.id != null

  const search = () => {
    const n = Number(adminIdInput)
    setAdminId(adminIdInput.trim() && Number.isFinite(n) ? n : undefined)
    setPageNum(1)
  }

  const openCreate = () => {
    setForm(emptyForm)
    setDialogOpen(true)
  }

  const openEdit = (row: AdminVO) => {
    setForm({
      id: row.id,
      username: row.username,
      password: '',
      realName: row.realName || '',
      role: row.role,
      status: row.status ?? ADMIN_STATUS_ENABLED
    })
    setDialogOpen(true)
  }

  const submit = async () => {
    if (!isEdit) {
      if (!form.username.trim()) {
        toast.warning('请填写登录账号')
        return
      }
      if (form.password.length < 8) {
        toast.warning('初始密码至少 8 位')
        return
      }
      if (!form.realName.trim()) {
        toast.warning('请填写真实姓名')
        return
      }
      try {
        await createAccount({
          username: form.username.trim(),
          password: form.password,
          realName: form.realName.trim(),
          role: form.role
        }).unwrap()
        toast.success('账号创建成功')
        setDialogOpen(false)
      } catch {
        /* baseQuery 已提示 */
      }
      return
    }

    if (!form.realName.trim()) {
      toast.warning('请填写真实姓名')
      return
    }
    try {
      await updateAccount({ id: form.id!, realName: form.realName.trim(), role: form.role, status: form.status }).unwrap()
      toast.success('账号信息已更新')
      setDialogOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const toggleStatus = async (row: AdminVO) => {
    const disabling = row.status === ADMIN_STATUS_ENABLED
    const nextStatus = disabling ? ADMIN_STATUS_DISABLED : ADMIN_STATUS_ENABLED
    const ok = await confirm({
      title: disabling ? '停用账号' : '启用账号',
      description: disabling
        ? `确认停用「${row.realName || row.username}」？停用后该管理员将无法登录后台。`
        : `确认启用「${row.realName || row.username}」？`,
      confirmText: disabling ? '确认停用' : '确认启用',
      tone: disabling ? 'destructive' : 'default'
    })
    if (!ok) return
    try {
      await updateAccount({
        id: row.id,
        realName: row.realName || row.username,
        role: row.role,
        status: nextStatus
      }).unwrap()
      toast.success(disabling ? '已停用' : '已启用')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const submitResetPassword = async () => {
    if (!pwdTarget) return
    if (newPassword.length < 8) {
      toast.warning('新密码至少 8 位')
      return
    }
    try {
      await resetPassword({ id: pwdTarget.id, password: newPassword }).unwrap()
      toast.success('密码已重置，请通知该管理员尽快修改')
      setPwdTarget(null)
      setNewPassword('')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="管理员管理"
        description="后台账号创建、角色分配、启停与密码重置（ADM-01，超级管理员专属）"
        actions={
          <Button onClick={openCreate}>
            <UserPlus className="size-4" />
            新建管理员
          </Button>
        }
      />

      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">管理员 ID</Label>
            <Input
              value={adminIdInput}
              placeholder="按 ID 精确查询"
              className="w-[180px]"
              onChange={(e) => setAdminIdInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') search()
              }}
            />
          </div>
          <Button onClick={search}>
            <Search className="size-4" />
            查询
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setAdminIdInput('')
              setAdminId(undefined)
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
                <TableHead className="w-[160px]">登录账号</TableHead>
                <TableHead className="w-[150px]">真实姓名</TableHead>
                <TableHead className="w-[130px]">角色</TableHead>
                <TableHead className="w-[120px]">状态</TableHead>
                <TableHead className="w-[150px]">创建时间</TableHead>
                <TableHead className="w-[280px] text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const meta = ROLE_META[row.role]
                return (
                  <TableRow key={row.id}>
                    <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                    <TableCell className="font-medium">{row.username}</TableCell>
                    <TableCell>{row.realName || '—'}</TableCell>
                    <TableCell>
                      <StatusBadge label={meta?.label || row.role} tone={meta?.badge || 'soft'} />
                    </TableCell>
                    <TableCell>
                      {row.status === ADMIN_STATUS_DISABLED ? (
                        <StatusBadge label="已停用" tone="destructive" />
                      ) : (
                        <StatusBadge label="正常" tone="success" />
                      )}
                    </TableCell>
                    <TableCell>
                      <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1.5">
                        <Button variant="ghost" size="sm" onClick={() => openEdit(row)}>
                          <Pencil className="size-3.5" />
                          编辑
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setPwdTarget(row)
                            setNewPassword('')
                          }}
                        >
                          <KeyRound className="size-3.5" />
                          重置密码
                        </Button>
                        <Button
                          variant={row.status === ADMIN_STATUS_DISABLED ? 'outline' : 'ghost'}
                          size="sm"
                          className={row.status === ADMIN_STATUS_DISABLED ? '' : 'text-destructive hover:bg-destructive/10 hover:text-destructive'}
                          onClick={() => toggleStatus(row)}
                        >
                          {row.status === ADMIN_STATUS_DISABLED ? <CheckCircle2 className="size-3.5" /> : <Ban className="size-3.5" />}
                          {row.status === ADMIN_STATUS_DISABLED ? '启用' : '停用'}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        ) : (
          <EmptyState title="暂无管理员" description="点击右上角新建管理员账号" />
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

      {/* 新建 / 编辑 */}
      <Dialog open={dialogOpen} onOpenChange={(open) => !open && setDialogOpen(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{isEdit ? '编辑管理员' : '新建管理员'}</DialogTitle>
            <DialogDescription>
              {isEdit ? '可调整真实姓名与角色（角色变更按 RBAC 生效）' : '创建后系统将标记为须首次登录修改密码'}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">登录账号</Label>
              <Input
                value={form.username}
                disabled={isEdit}
                placeholder="登录账号（创建后不可修改）"
                onChange={(e) => setForm({ ...form, username: e.target.value })}
              />
            </div>

            {!isEdit ? (
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">初始密码</Label>
                <Input
                  type="password"
                  value={form.password}
                  placeholder="至少 8 位"
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                />
              </div>
            ) : null}

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">真实姓名</Label>
              <Input
                value={form.realName}
                placeholder="用于操作日志署名"
                onChange={(e) => setForm({ ...form, realName: e.target.value })}
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">角色</Label>
              <Select value={form.role} onValueChange={(v) => setForm({ ...form, role: v as AdminRole })}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ALL_ROLES.map((role) => (
                    <SelectItem key={role} value={role}>
                      {ROLE_META[role].label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-[11px] text-muted-foreground">{ROLE_META[form.role].desc}</p>
            </div>

            {isEdit ? (
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">账号状态</Label>
                <Select value={String(form.status)} onValueChange={(v) => setForm({ ...form, status: Number(v) })}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={String(ADMIN_STATUS_ENABLED)}>正常</SelectItem>
                    <SelectItem value={String(ADMIN_STATUS_DISABLED)}>停用</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={creating || updating} onClick={submit}>
              {isEdit ? '保存修改' : '创建账号'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 重置密码 */}
      <Dialog
        open={!!pwdTarget}
        onOpenChange={(open) => {
          if (!open) {
            setPwdTarget(null)
            setNewPassword('')
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>重置密码</DialogTitle>
            <DialogDescription>
              {pwdTarget ? `为「${pwdTarget.realName || pwdTarget.username}」设置新的登录密码` : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">新密码</Label>
            <Input
              type="password"
              value={newPassword}
              placeholder="至少 8 位"
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <p className="text-[11px] text-muted-foreground">重置后该管理员将被标记为须修改密码。</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPwdTarget(null)}>
              取消
            </Button>
            <Button disabled={resetting} onClick={submitResetPassword}>
              确认重置
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
