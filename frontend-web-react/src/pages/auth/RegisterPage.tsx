import { useState } from 'react'
import { RouteLink as Link, useRouteNavigate } from '@/components/RouteLink'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Eye, EyeOff, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAppDispatch } from '@/app/hooks'
import { setCredentials } from '@/features/auth/authSlice'
import { useRegisterMutation } from '@/api/authApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { RegisterDTO } from '@/api/types'

/** 校验规则与提示文案与 Vue 版 RegisterView 的 el-form rules 逐字一致 */
const schema = z
  .object({
    username: z
      .string()
      .min(1, '请输入用户名')
      .min(3, '长度须为 3~50 个字符')
      .max(50, '长度须为 3~50 个字符'),
    nickname: z.string().min(1, '请输入昵称'),
    password: z
      .string()
      .min(1, '请输入密码')
      .min(6, '长度须为 6~64 个字符')
      .max(64, '长度须为 6~64 个字符'),
    confirmPassword: z.string().min(1, '请再次输入密码')
  })
  .refine((d) => d.confirmPassword === d.password, {
    path: ['confirmPassword'],
    message: '两次输入的密码不一致'
  })

type FormValues = z.infer<typeof schema>

/**
 * 注册页（`/register`，在 MainLayout 内）。
 * 与 Vue 版 views/auth/RegisterView.vue 对齐：4 条校验规则与提示、`data-testid`、
 * 注册成功后的"去认证"强引导（关闭弹窗也会跳首页，与 Vue 版 `.catch` 行为一致）。
 *
 * 注意：Vue 版会把 `confirmPassword` 一起 POST（后端 DTO 无该字段，被忽略）；
 * React 版**只提交契约字段**（见 docs/contracts-frontend-web.md §6 陷阱 #20）。
 */
export default function RegisterPage() {
  const dispatch = useAppDispatch()
  const navigate = useRouteNavigate()
  const [welcomeOpen, setWelcomeOpen] = useState(false)
  const [showPwd, setShowPwd] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [registerApi, { isLoading: loading }] = useRegisterMutation()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', nickname: '', password: '', confirmPassword: '' }
  })

  const onSubmit = form.handleSubmit(async (values) => {
    const body: RegisterDTO = {
      username: values.username,
      password: values.password,
      nickname: values.nickname
    }
    try {
      const data = await registerApi(body).unwrap()
      dispatch(setCredentials({ login: data, remember: true }))
      toast.success('注册成功')
      setWelcomeOpen(true)
    } catch {
      /* 错误提示由 baseQuery 统一给出 */
    }
  })

  const goVerify = () => {
    setWelcomeOpen(false)
    navigate('/verify')
  }

  return (
    <div className="page flex justify-center pt-12">
      <Card className="paper-card w-full max-w-[480px] p-6">
        <h2 className="m-0 text-center text-xl font-bold text-foreground">注册校园二手</h2>
        <p className="mt-1 mb-5 text-center text-[13px] text-muted-foreground">
          注册后完成校园认证即可发布、下单和聊天
        </p>

        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1.5">
            <Label htmlFor="username">用户名</Label>
            <Input
              id="username"
              data-testid="register-username"
              placeholder="3~50 个字符，注册后不可修改"
              {...form.register('username')}
            />
            {form.formState.errors.username ? (
              <p className="text-xs text-destructive">{form.formState.errors.username.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="nickname">昵称</Label>
            <Input
              id="nickname"
              data-testid="register-nickname"
              placeholder="展示在商品与聊天中的名字"
              {...form.register('nickname')}
            />
            {form.formState.errors.nickname ? (
              <p className="text-xs text-destructive">{form.formState.errors.nickname.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">密码</Label>
            <div className="flex items-center gap-2">
              <Input
                id="password"
                data-testid="register-password"
                type={showPwd ? 'text' : 'password'}
                placeholder="6~64 位"
                {...form.register('password')}
              />
              <button
                type="button"
                aria-label={showPwd ? '隐藏密码' : '显示密码'}
                onClick={() => setShowPwd((v) => !v)}
                className="shrink-0 rounded-md p-2 text-muted-foreground transition hover:text-foreground"
              >
                {showPwd ? <EyeOff className="size-[18px]" /> : <Eye className="size-[18px]" />}
              </button>
            </div>
            {form.formState.errors.password ? (
              <p className="text-xs text-destructive">{form.formState.errors.password.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">确认密码</Label>
            <div className="flex items-center gap-2">
              <Input
                id="confirmPassword"
                data-testid="register-confirm"
                type={showConfirm ? 'text' : 'password'}
                placeholder="再输入一次"
                {...form.register('confirmPassword')}
              />
              <button
                type="button"
                aria-label={showConfirm ? '隐藏密码' : '显示密码'}
                onClick={() => setShowConfirm((v) => !v)}
                className="shrink-0 rounded-md p-2 text-muted-foreground transition hover:text-foreground"
              >
                {showConfirm ? <EyeOff className="size-[18px]" /> : <Eye className="size-[18px]" />}
              </button>
            </div>
            {form.formState.errors.confirmPassword ? (
              <p className="text-xs text-destructive">{form.formState.errors.confirmPassword.message}</p>
            ) : null}
          </div>

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? <Loader2 className="size-4 animate-spin" /> : null}
            注 册
          </Button>
        </form>

        <div className="mt-4 text-center">
          <Link to="/login" className="text-[13px] text-primary hover:underline">
            已有账号？去登录
          </Link>
        </div>
      </Card>

      {/* 注册成功强引导校园认证（Vue 版 ElMessageBox.alert，关闭也跳首页） */}
      <Dialog open={welcomeOpen} onOpenChange={(open) => !open && navigate('/')}>
        <DialogContent showCloseButton={false} className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>欢迎加入！</DialogTitle>
            <DialogDescription className="leading-relaxed text-foreground">
              发布商品、下单和聊天需要先完成校园认证（学号 + 校园邮箱验证码）。
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => navigate('/')}>
              稍后再说
            </Button>
            <Button onClick={goVerify}>去认证</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
