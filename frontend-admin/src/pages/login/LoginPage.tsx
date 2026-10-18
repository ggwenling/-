import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { KeyRound, Loader2, LogIn, Store } from 'lucide-react'
import { useAppDispatch } from '@/app/hooks'
import { setAdmin, setCredentials } from '@/features/auth/authSlice'
import { useChangePasswordMutation, useLazyGetMeQuery, useLoginMutation } from '@/api/authApi'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'

const loginSchema = z.object({
  username: z.string().min(1, '请输入账号'),
  password: z.string().min(1, '请输入密码')
})
type LoginForm = z.infer<typeof loginSchema>

const pwdSchema = z
  .object({
    oldPassword: z.string().min(1, '请输入原密码'),
    newPassword: z.string().min(8, '新密码至少 8 位'),
    confirmPassword: z.string().min(1, '请再次输入新密码')
  })
  .refine((d) => d.newPassword !== d.oldPassword, { path: ['newPassword'], message: '新密码不能与原密码相同' })
  .refine((d) => d.newPassword === d.confirmPassword, { path: ['confirmPassword'], message: '两次输入的密码不一致' })
type PwdForm = z.infer<typeof pwdSchema>

export default function LoginPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()

  const [loginApi, { isLoading }] = useLoginMutation()
  const [changePasswordApi, { isLoading: changing }] = useChangePasswordMutation()
  const [fetchMe] = useLazyGetMeQuery()

  const [pwdOpen, setPwdOpen] = useState(false)

  const loginForm = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { username: '', password: '' } })
  const pwdForm = useForm<PwdForm>({
    resolver: zodResolver(pwdSchema),
    defaultValues: { oldPassword: '', newPassword: '', confirmPassword: '' }
  })

  const redirectTo = params.get('redirect') || '/dashboard'

  const onLogin = loginForm.handleSubmit(async (values) => {
    try {
      const res = await loginApi(values).unwrap()
      dispatch(setCredentials(res))
      if (res.mustChangePassword) {
        // T12：先弹强改密门禁，改密成功后再放行
        pwdForm.reset({ oldPassword: '', newPassword: '', confirmPassword: '' })
        setPwdOpen(true)
        return
      }
      toast.success('登录成功')
      navigate(redirectTo, { replace: true })
    } catch {
      // 错误提示由 baseQuery 统一 toast
    }
  })

  const onChangePassword = pwdForm.handleSubmit(async (values) => {
    try {
      await changePasswordApi({ oldPassword: values.oldPassword, newPassword: values.newPassword }).unwrap()
      const me = await fetchMe().unwrap().catch(() => null)
      if (me) dispatch(setAdmin(me)) // 刷新 mustChangePassword 标记
      setPwdOpen(false)
      toast.success('密码修改成功，欢迎进入后台')
      navigate(redirectTo, { replace: true })
    } catch {
      // 错误提示由 baseQuery 统一 toast
    }
  })

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-10">
      {/* 背景点缀：布告栏纸片 */}
      <div className="pointer-events-none absolute -top-16 -left-10 size-64 rotate-6 rounded-3xl border border-dashed border-[#d8cbb0] bg-secondary/30" />
      <div className="pointer-events-none absolute -right-12 bottom-0 size-72 -rotate-6 rounded-3xl border border-dashed border-[#d8cbb0] bg-secondary/20" />

      <div className="paper-card paper-fold relative w-full max-w-[400px] px-7 py-8">
        <div className="mb-6 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-xs">
            <Store className="size-6" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-foreground">管理后台登录</h1>
            <p className="mt-1 text-xs text-muted-foreground">校园二手交易平台 · 运营管理中心</p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={onLogin}>
          <div className="space-y-1.5">
            <Label htmlFor="username">账号</Label>
            <Input
              id="username"
              autoComplete="username"
              placeholder="管理员账号"
              data-testid="admin-login-username"
              {...loginForm.register('username')}
            />
            {loginForm.formState.errors.username ? (
              <p className="text-xs text-destructive">{loginForm.formState.errors.username.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">密码</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="密码"
              data-testid="admin-login-password"
              {...loginForm.register('password')}
            />
            {loginForm.formState.errors.password ? (
              <p className="text-xs text-destructive">{loginForm.formState.errors.password.message}</p>
            ) : null}
          </div>

          <Button type="submit" className="w-full" disabled={isLoading}>
            {isLoading ? <Loader2 className="size-4 animate-spin" /> : <LogIn className="size-4" />}
            登 录
          </Button>
        </form>

        <p className="mt-5 text-center text-[11px] leading-relaxed text-muted-foreground">
          后台与前台登录相互独立（ADM-01）
          <br />
          角色权限见 RBAC 权限矩阵
        </p>
      </div>

      {/* T12 强制改密门禁：不可关闭 */}
      <Dialog open={pwdOpen} onOpenChange={() => undefined}>
        <DialogContent
          showCloseButton={false}
          onEscapeKeyDown={(e) => e.preventDefault()}
          onPointerDownOutside={(e) => e.preventDefault()}
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <KeyRound className="size-4 text-accent" />
              首次登录请先修改密码
            </DialogTitle>
            <DialogDescription>
              当前账号被标记为须修改密码（首次登录或被重置密码），修改成功后才能继续使用后台。
            </DialogDescription>
          </DialogHeader>

          <form className="space-y-4" onSubmit={onChangePassword}>
            <div className="space-y-1.5">
              <Label htmlFor="oldPassword">原密码</Label>
              <Input id="oldPassword" type="password" placeholder="请输入当前密码" {...pwdForm.register('oldPassword')} />
              {pwdForm.formState.errors.oldPassword ? (
                <p className="text-xs text-destructive">{pwdForm.formState.errors.oldPassword.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="newPassword">新密码</Label>
              <Input id="newPassword" type="password" placeholder="至少 8 位" {...pwdForm.register('newPassword')} />
              {pwdForm.formState.errors.newPassword ? (
                <p className="text-xs text-destructive">{pwdForm.formState.errors.newPassword.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword">确认新密码</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="再次输入新密码"
                {...pwdForm.register('confirmPassword')}
              />
              {pwdForm.formState.errors.confirmPassword ? (
                <p className="text-xs text-destructive">{pwdForm.formState.errors.confirmPassword.message}</p>
              ) : null}
            </div>
            <Button type="submit" className="w-full" disabled={changing}>
              {changing ? <Loader2 className="size-4 animate-spin" /> : null}
              确认修改
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
