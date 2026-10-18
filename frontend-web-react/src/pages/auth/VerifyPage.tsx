import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { CheckCircle2, Info, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectIsVerified, setProfile } from '@/features/auth/authSlice'
import { useLazyGetMeQuery, useSendVerifyCodeMutation, useVerifyCodeMutation } from '@/api/authApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** 校验规则与提示文案与 Vue 版 VerifyView 的 el-form rules 逐字一致 */
const schema = z.object({
  studentNo: z.string().min(1, '请输入学号'),
  campusEmail: z.string().min(1, '请输入校园邮箱').email('邮箱格式不正确'),
  code: z
    .string()
    .min(1, '请输入验证码')
    .regex(/^\d{6}$/, '验证码为 6 位数字')
})

type FormValues = z.infer<typeof schema>

/**
 * 校园认证页（`/verify`，在 MainLayout 内，需登录）。
 * 与 Vue 版 views/auth/VerifyView.vue 对齐：演示模式提示、已认证短路、60s 倒计时、
 * 3 个 `data-testid`、发送前的"请先填写学号和校园邮箱"前置校验。
 */
export default function VerifyPage() {
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const isVerified = useAppSelector(selectIsVerified)
  const isDemo = import.meta.env.VITE_DEMO_MODE === 'true'

  const [countdown, setCountdown] = useState(0)
  const timerRef = useRef<number | null>(null)

  const [sendCode, { isLoading: sending }] = useSendVerifyCodeMutation()
  const [verifyCode, { isLoading: loading }] = useVerifyCodeMutation()
  const [fetchMe] = useLazyGetMeQuery()

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { studentNo: '', campusEmail: '', code: '' }
  })

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) window.clearInterval(timerRef.current)
    }
  }, [])

  const startCountdown = () => {
    setCountdown(60)
    if (timerRef.current !== null) window.clearInterval(timerRef.current)
    timerRef.current = window.setInterval(() => {
      setCountdown((v) => {
        if (v <= 1) {
          if (timerRef.current !== null) window.clearInterval(timerRef.current)
          return 0
        }
        return v - 1
      })
    }, 1000)
  }

  const handleSend = async () => {
    const { studentNo, campusEmail } = form.getValues()
    if (!studentNo || !campusEmail) {
      toast.warning('请先填写学号和校园邮箱')
      return
    }
    try {
      await sendCode({ studentNo, campusEmail }).unwrap()
      toast.success('验证码已发送')
      startCountdown()
    } catch {
      /* 错误提示由 baseQuery 统一给出（非校园邮箱 40007 / 重发过频 42901） */
    }
  }

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      await verifyCode(values).unwrap()
      toast.success('校园认证通过！')
      const me = await fetchMe().unwrap().catch(() => null)
      if (me) dispatch(setProfile(me))
      navigate('/profile')
    } catch {
      /* 错误提示由 baseQuery 统一给出 */
    }
  })

  if (isVerified) {
    return (
      <div className="page flex justify-center pt-12">
        <Card className="paper-card w-full max-w-[480px] p-8 text-center">
          <CheckCircle2 className="mx-auto size-12 text-[var(--success)]" />
          <h2 className="mt-4 text-xl font-bold text-foreground">已完成校园认证</h2>
          <p className="mt-1 text-[13px] text-muted-foreground">可以去发布商品了</p>
          <div className="mt-6 flex justify-center gap-3">
            <Button onClick={() => navigate('/publish')}>去发布</Button>
            <Button variant="outline" onClick={() => navigate('/profile')}>
              返回个人中心
            </Button>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="page flex justify-center pt-12">
      <Card className="paper-card w-full max-w-[480px] p-6">
        <h2 className="m-0 text-center text-xl font-bold text-foreground">校园认证</h2>
        <p className="mt-1 mb-4 text-center text-[13px] text-muted-foreground">
          学号 + 校园邮箱验证码，认证后才能发布、下单和聊天（PRD §4.1）
        </p>

        {isDemo ? (
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-[var(--info)]/25 bg-[var(--info)]/8 px-3 py-2.5 text-[13px] text-[var(--info)]">
            <Info className="mt-0.5 size-4 shrink-0" />
            演示环境：验证码已打印到后端控制台日志，无需真实邮箱
          </div>
        ) : null}

        <form className="space-y-4" onSubmit={onSubmit}>
          <div className="space-y-1.5">
            <Label htmlFor="studentNo">学号</Label>
            <Input
              id="studentNo"
              data-testid="verify-student-no"
              placeholder="学号（全局唯一，认证后不可更改）"
              {...form.register('studentNo')}
            />
            {form.formState.errors.studentNo ? (
              <p className="text-xs text-destructive">{form.formState.errors.studentNo.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="campusEmail">校园邮箱</Label>
            <Input
              id="campusEmail"
              data-testid="verify-email"
              placeholder="例如 20240001@stu.example.edu.cn"
              {...form.register('campusEmail')}
            />
            {form.formState.errors.campusEmail ? (
              <p className="text-xs text-destructive">{form.formState.errors.campusEmail.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="code">验证码</Label>
            <div className="flex items-center gap-3">
              <Input
                id="code"
                data-testid="verify-code"
                maxLength={6}
                placeholder="6 位数字"
                {...form.register('code')}
              />
              <Button type="button" variant="outline" disabled={countdown > 0 || sending} onClick={handleSend}>
                {sending ? <Loader2 className="size-4 animate-spin" /> : null}
                {countdown > 0 ? `${countdown}s 后重发` : '发送验证码'}
              </Button>
            </div>
            {form.formState.errors.code ? (
              <p className="text-xs text-destructive">{form.formState.errors.code.message}</p>
            ) : null}
          </div>

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? <Loader2 className="size-4 animate-spin" /> : null}
            完成认证
          </Button>
        </form>
      </Card>
    </div>
  )
}
