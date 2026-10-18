import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RouteLink as Link, useRouteNavigate } from '@/components/RouteLink'
import { Check, Eye, EyeOff, Loader2, Lock, Sparkles, User } from 'lucide-react'
import { toast } from 'sonner'
import { useAppDispatch } from '@/app/hooks'
import { setCredentials } from '@/features/auth/authSlice'
import { useLoginMutation } from '@/api/authApi'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

/** 协议占位文案（PRD 范围外，仅弹窗展示）——与 Vue 版 LoginView 逐字一致 */
const AGREEMENTS = {
  service: {
    title: '用户协议',
    text: '欢迎使用校园二手交易系统。本平台面向经过校园邮箱认证的在校学生，提供闲置物品发布、求购、交换与信息撮合服务。平台不收款、不托管资金、不提供物流，交易双方在线完成信息确认后于线下当面交割。用户发布的信息须真实合法，不得发布违禁或侵权商品；违反平台规则将受到下架、扣分、限制或封禁等处置。演示环境本协议为占位文案。'
  },
  privacy: {
    title: '隐私政策',
    text: '平台仅收集完成交易所必需的信息：账号资料、校园认证信息（学号与校园邮箱，对外脱敏展示）、商品与订单数据、聊天记录。你的信息不会被出售或提供给任何第三方；信用分与评价仅用于维护校园交易秩序。你可以在个人中心查看并修改资料。演示环境本政策为占位文案。'
  }
} as const

/** 左侧纸感拼贴（替代 Vue 版的 login-hero.png 插画，遵循"纸感 CSS 构图"决策） */
function PaperCollage() {
  const notes = [
    { title: '教材转让', desc: '九成新 · 笔记齐全', rotate: '-2.4deg', tag: '急出可刀' },
    { title: '宿舍好物', desc: '台灯 / 键盘 / 小家电', rotate: '1.8deg', tag: '可小刀' },
    { title: '球拍换书', desc: '接受补差价', rotate: '-1.2deg', tag: '可补差价' }
  ]
  return (
    <aside className="notice-board relative hidden w-[45%] overflow-hidden border-r border-border bg-secondary/40 lg:block">
      <div className="absolute inset-0 p-10">
        <div className="flex items-center gap-3">
          <span className="grid size-11 place-items-center rounded-xl bg-primary text-lg font-extrabold text-primary-foreground shadow-md">
            闲
          </span>
          <span>
            <strong className="block text-lg leading-tight text-foreground">校园淘</strong>
            <small className="text-[11px] text-muted-foreground">让闲置回到校园</small>
          </span>
        </div>

        <h2 className="mt-10 text-[30px] leading-snug font-bold text-foreground">
          把用不上的，
          <br />
          <span className="text-primary">交给刚好需要的人。</span>
        </h2>
        <p className="mt-3 text-[13px] text-muted-foreground">校园认证 · 线下面交 · 平台不经手资金</p>

        <div className="mt-10 space-y-4">
          {notes.map((n) => (
            <div
              key={n.title}
              className="paper-card paper-fold relative p-4 pr-16"
              style={{ transform: `rotate(${n.rotate})` }}
            >
              <div className="text-[15px] font-semibold text-foreground">{n.title}</div>
              <div className="mt-0.5 text-[12px] text-muted-foreground">{n.desc}</div>
              <span className="tape-tag absolute top-3 right-3">{n.tag}</span>
            </div>
          ))}
        </div>

        <div className="mt-10 flex items-center gap-3">
          <span className="stamp-badge">校园认证</span>
          <span className="stamp-badge">信用分体系</span>
        </div>
      </div>
    </aside>
  )
}

/**
 * 登录页（`/login`，整屏裸路由，不在 MainLayout 内）。
 * 与 Vue 版 views/auth/LoginView.vue 逐字对齐：校验顺序、文案、错误处理、协议弹窗、
 * `data-testid`（login-username / login-password / login-forgot）与 remember 语义。
 *
 * 唯一刻意差异（见 docs/contracts-frontend-web.md §7 与方案的 URL 契约变更）：
 * 登录成功后默认跳 `/home`（商城主页；Vue 版此处是 `/`，而 `/` 现在是落地页）。
 */
export default function LoginPage() {
  const dispatch = useAppDispatch()
  const navigate = useRouteNavigate()
  const [params] = useSearchParams()

  const [tab, setTab] = useState('password')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [remember, setRemember] = useState(true)
  const [agree, setAgree] = useState(false)
  const [showPwd, setShowPwd] = useState(false)
  const [agreeDialog, setAgreeDialog] = useState<{ title: string; text: string } | null>(null)
  /** 封禁通知弹窗（对应 Vue 版 ElMessageBox.alert(reason, '封禁通知')） */
  const [banAlert, setBanAlert] = useState<string | null>(null)

  const [loginApi, { isLoading: loading }] = useLoginMutation()

  /** PRD §11 范围外能力：验证码登录/微信/QQ/忘记密码，保留视觉，点击提示 */
  const notSupported = () => toast.info('演示环境暂不支持')

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!agree) {
      toast.warning('请先阅读并同意《用户协议》和《隐私政策》')
      return
    }
    if (tab === 'code') {
      notSupported()
      return
    }
    if (!username || !password) {
      toast.warning('请输入用户名和密码')
      return
    }
    try {
      const data = await loginApi({ username, password }).unwrap()
      dispatch(setCredentials({ login: data, remember }))
      if (data.banned) {
        // 封禁用户可登录但功能被拦截（PRD §4.1），登录即展示封禁通知（与 Vue 版同：弹窗后照常跳转）
        setBanAlert(data.banReason || '账号已被封禁')
      } else {
        toast.success('登录成功')
      }
      navigate(params.get('redirect') || '/home', { replace: true })
    } catch {
      /* 错误提示由 baseQuery 统一给出 */
    }
  }

  return (
    <div className="route-page flex min-h-screen w-full bg-background text-foreground">
      <PaperCollage />

      <main className="relative flex flex-1 items-center justify-center px-6 py-16">
        <div className="absolute top-6 right-6 text-sm text-muted-foreground md:top-8 md:right-10">
          没有账号？
          <Link to="/register" className="ml-1 font-medium text-primary transition-colors hover:text-primary/80">
            立即注册
          </Link>
        </div>

        <div className="w-full max-w-[400px] animate-fade-up">
          <h1 className="m-0 text-[28px] leading-9 font-semibold tracking-tight">欢迎登录</h1>
          <p className="mt-2 text-[15px] text-muted-foreground">登录后即可发布商品、与同学交易</p>

          <Tabs value={tab} onValueChange={setTab} className="mt-8">
            <TabsList className="w-full justify-start gap-8 border-b border-border bg-transparent p-0">
              <TabsTrigger value="password" className="font-medium">
                密码登录
              </TabsTrigger>
              <TabsTrigger value="code" className="font-medium">
                验证码登录
              </TabsTrigger>
            </TabsList>

            <TabsContent value="password">
              <form className="mt-7 space-y-4" onSubmit={handleLogin}>
                <div className="flex h-10 items-center gap-2 rounded-xl border border-input bg-card px-3 focus-within:border-primary">
                  <User className="size-4 shrink-0 text-muted-foreground" />
                  <Input
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    data-testid="login-username"
                    placeholder="请输入用户名"
                    autoComplete="username"
                    className="h-9 border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
                  />
                </div>

                <div className="flex h-10 items-center gap-2 rounded-xl border border-input bg-card px-3 focus-within:border-primary">
                  <Lock className="size-4 shrink-0 text-muted-foreground" />
                  <Input
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    data-testid="login-password"
                    type={showPwd ? 'text' : 'password'}
                    placeholder="请输入密码"
                    autoComplete="current-password"
                    className="h-9 border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
                  />
                  <button
                    type="button"
                    aria-label={showPwd ? '隐藏密码' : '显示密码'}
                    onClick={() => setShowPwd((v) => !v)}
                    className="shrink-0 rounded-md p-1 text-muted-foreground transition hover:text-foreground"
                  >
                    {showPwd ? <EyeOff className="size-[18px]" /> : <Eye className="size-[18px]" />}
                  </button>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-2 select-none">
                    <button
                      type="button"
                      role="checkbox"
                      aria-checked={remember}
                      onClick={() => setRemember((v) => !v)}
                      className="grid size-4 place-items-center rounded-[4px] border border-input bg-card data-[checked=true]:border-primary data-[checked=true]:bg-primary data-[checked=true]:text-primary-foreground"
                      data-checked={remember}
                    >
                      {remember ? <Check className="size-3" /> : null}
                    </button>
                    <span
                      className="cursor-pointer text-sm text-muted-foreground transition-colors hover:text-foreground"
                      onClick={() => setRemember((v) => !v)}
                    >
                      记住登录状态
                    </span>
                  </div>
                  <button
                    type="button"
                    data-testid="login-forgot"
                    onClick={notSupported}
                    className="text-sm text-muted-foreground transition-colors hover:text-primary"
                  >
                    忘记密码？
                  </button>
                </div>

                <Button type="submit" size="lg" disabled={loading} className="w-full text-base font-medium">
                  {loading ? <Loader2 className="size-[18px] animate-spin" /> : null}
                  {loading ? '登录中…' : '登录'}
                </Button>
              </form>
            </TabsContent>

            <TabsContent value="code">
              <form className="mt-7 space-y-4" onSubmit={handleLogin}>
                <div className="flex h-10 items-center gap-2 rounded-xl border border-input bg-card px-3 focus-within:border-primary">
                  <User className="size-4 shrink-0 text-muted-foreground" />
                  <Input
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="请输入手机号或学号"
                    autoComplete="tel"
                    className="h-9 border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
                  />
                </div>

                <div className="flex h-10 items-center gap-2 rounded-xl border border-input bg-card px-3 focus-within:border-primary">
                  <Input
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    placeholder="请输入验证码"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    className="h-9 border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
                  />
                  <button
                    type="button"
                    onClick={notSupported}
                    className="shrink-0 px-2 text-[13px] text-primary hover:underline"
                  >
                    获取验证码
                  </button>
                </div>

                <Button type="submit" size="lg" className="w-full text-base font-medium">
                  登录
                </Button>
              </form>
            </TabsContent>
          </Tabs>

          <div className="mt-8 flex items-center gap-4">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs text-muted-foreground">或</span>
            <span className="h-px flex-1 bg-border" />
          </div>
          <div className="mt-4 text-center text-[13px] text-muted-foreground">其他方式登录</div>
          <div className="mt-4 flex items-start justify-center gap-8">
            <button type="button" aria-label="微信登录" onClick={notSupported} className="group grid justify-items-center gap-2">
              <span className="grid size-12 place-items-center rounded-full bg-[#09BB07] text-[20px] text-white shadow-sm transition-transform group-hover:-translate-y-0.5">
                微
              </span>
              <span className="text-[13px] text-muted-foreground">微信登录</span>
            </button>
            <button type="button" aria-label="QQ登录" onClick={notSupported} className="group grid justify-items-center gap-2">
              <span className="grid size-12 place-items-center rounded-full bg-[#12B7F5] text-[20px] text-white shadow-sm transition-transform group-hover:-translate-y-0.5">
                Q
              </span>
              <span className="text-[13px] text-muted-foreground">QQ登录</span>
            </button>
          </div>

          <div className="mt-8 flex items-center gap-2 text-[13px] leading-5 text-muted-foreground select-none">
            <button
              type="button"
              role="checkbox"
              aria-checked={agree}
              onClick={() => setAgree((v) => !v)}
              className="grid size-4 shrink-0 place-items-center rounded-[4px] border border-input bg-card data-[checked=true]:border-primary data-[checked=true]:bg-primary data-[checked=true]:text-primary-foreground"
              data-checked={agree}
            >
              {agree ? <Check className="size-3" /> : null}
            </button>
            <span>
              我已阅读并同意
              <button
                type="button"
                className="mx-0.5 text-[13px] text-primary hover:underline"
                onClick={() => setAgreeDialog(AGREEMENTS.service)}
              >
                《用户协议》
              </button>
              和
              <button
                type="button"
                className="mx-0.5 text-[13px] text-primary hover:underline"
                onClick={() => setAgreeDialog(AGREEMENTS.privacy)}
              >
                《隐私政策》
              </button>
            </span>
          </div>

          <p className="mt-6 flex items-center justify-center gap-1.5 text-[11px] text-muted-foreground">
            <Sparkles className="size-3" />
            演示环境：密码登录可用，验证码/第三方登录为占位
          </p>
        </div>
      </main>

      {/* 协议弹窗 */}
      <Dialog open={agreeDialog !== null} onOpenChange={(open) => !open && setAgreeDialog(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{agreeDialog?.title}</DialogTitle>
            <DialogDescription className="max-h-[50vh] overflow-y-auto leading-relaxed whitespace-pre-wrap">
              {agreeDialog?.text}
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end">
            <Button variant="outline" onClick={() => setAgreeDialog(null)}>
              我知道了
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 封禁通知（登录响应 banned=true 时） */}
      <Dialog open={banAlert !== null} onOpenChange={(open) => !open && setBanAlert(null)}>
        <DialogContent showCloseButton={false} className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="text-destructive">封禁通知</DialogTitle>
            <DialogDescription className="leading-relaxed text-foreground">{banAlert}</DialogDescription>
          </DialogHeader>
          <div className="flex justify-end">
            <Button onClick={() => setBanAlert(null)}>我知道了</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
