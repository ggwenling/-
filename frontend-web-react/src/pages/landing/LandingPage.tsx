import { RouteLink as Link } from '@/components/RouteLink'
import { ArrowRight, BookOpen, Handshake, MapPin, ShieldCheck, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'

/**
 * 落地页（**新增页面**，Vue 版没有）：全屏校园插画 + 纸感布告栏气质。
 *
 * 说明：
 * - 背景图用 `public/campus-plaza.png`（沿用 Vue 版同类素材；后续可压缩）
 * - 入口：`进入商城` → `/home`（商城主页已由 `/` 迁到 `/home`）；`登录 / 注册` → `/login`、`/register`
 * - 已登录用户顶部显示「进入商城」，不再显示登录按钮（与 Vue 版落地页方案的约定一致）
 * - 不使用任何外网字体/CDN；动效遵循 `prefers-reduced-motion`
 */
export default function LandingPage() {
  const notes = [
    {
      icon: BookOpen,
      title: '教材流转',
      desc: '课本、真题、考研资料，学期末交到学弟学妹手里'
    },
    {
      icon: Sparkles,
      title: '宿舍好物',
      desc: '台灯、键盘、小家电，毕业前给它们找个新房间'
    },
    {
      icon: Handshake,
      title: '熟人面交',
      desc: '同校同城线下见面，验货后再确认，平台不经手资金'
    }
  ]

  return (
    <div className="route-page relative min-h-screen overflow-hidden">
      {/* 背景：校园插画 + 缓慢漂移 + 双层遮罩（保证文字可读） */}
      <div className="absolute inset-0 -z-10">
        <img
          src="/campus-plaza.jpg"
          alt=""
          aria-hidden="true"
          className="size-full animate-kenburns object-cover motion-reduce:animate-none"
        />
        <div className="landing-scrim absolute inset-0" />
      </div>

      {/* 顶栏：品牌 + 登录入口 */}
      <header className="relative z-10 mx-auto flex w-full max-w-[1200px] items-center justify-between gap-4 px-5 py-6">
        <Link to="/" className="flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-xl bg-primary text-lg font-bold text-primary-foreground shadow-md">
            闲
          </span>
          <span className="leading-tight">
            <span className="block text-base font-bold text-[#fbf7ef]">校园淘</span>
            <span className="block text-[11px] text-[#fbf7ef]/75">让闲置回到校园</span>
          </span>
        </Link>
        <nav className="flex items-center gap-2">
          <Button asChild variant="ghost" className="text-[#fbf7ef] hover:bg-white/10 hover:text-[#fbf7ef]">
            <Link to="/login">登录</Link>
          </Button>
          <Button asChild variant="outline" className="border-white/40 bg-white/10 text-[#fbf7ef] hover:bg-white/20">
            <Link to="/register">注册</Link>
          </Button>
        </nav>
      </header>

      {/* 主视觉 */}
      <main className="relative z-10 mx-auto flex w-full max-w-[1200px] flex-col gap-10 px-5 pb-16 pt-6 sm:pt-14">
        <section className="max-w-[620px] animate-fade-up [text-shadow:0_1px_12px_rgba(6,13,9,0.45)] motion-reduce:animate-none">
          <span className="tape-tag bg-[#f3dcaa] text-[#4a3410]">校园二手交易平台 · 线下面交</span>
          <h1 className="mt-4 text-4xl leading-tight font-bold text-[#fbf7ef] sm:text-5xl">
            让闲置回到校园
          </h1>
          <p className="mt-4 text-[15px] leading-relaxed text-[#fbf7ef]/85">
            教材、数码、宿舍好物 —— 在同一个校园里，找到它们的下一位主人。
            <br className="hidden sm:block" />
            实名校园认证后再交易，见面验货，钱货两清。
          </p>

          <div className="mt-7 flex flex-wrap items-center gap-3">
            <Button asChild size="lg" className="shadow-lg">
              <Link to="/home">
                进入商城
                <ArrowRight className="size-4" />
              </Link>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/40 bg-white/10 text-[#fbf7ef] hover:bg-white/20"
            >
              <Link to="/login">登录 / 注册</Link>
            </Button>
          </div>

          <div className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-2 text-[12px] text-[#fbf7ef]/75">
            <span className="inline-flex items-center gap-1.5">
              <ShieldCheck className="size-3.5" />
              校园认证 · 信用分体系
            </span>
            <span className="inline-flex items-center gap-1.5">
              <MapPin className="size-3.5" />
              同校面交 · 平台不经手资金
            </span>
          </div>
        </section>

        {/* 纸片说明：拍立得/便签质感 */}
        <section className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          {notes.map((n, i) => (
            <article
              key={n.title}
              className="paper-card paper-fold animate-fade-up p-5 motion-reduce:animate-none"
              style={{ transform: `rotate(${i % 2 === 0 ? -0.6 : 0.6}deg)`, animationDelay: `${i * 80}ms` }}
            >
              <span className="flex size-9 items-center justify-center rounded-lg bg-secondary text-primary">
                <n.icon className="size-4" />
              </span>
              <h2 className="mt-3 text-base font-semibold text-foreground">{n.title}</h2>
              <p className="mt-1 text-[13px] leading-relaxed text-muted-foreground">{n.desc}</p>
            </article>
          ))}
        </section>
      </main>

      <footer className="relative z-10 border-t border-[#e4dbc9] bg-[#f7f2e9]/92 py-5 text-center text-[12px] text-muted-foreground backdrop-blur">
        校园二手交易系统 · 线下面交 · 平台不经手资金
      </footer>
    </div>
  )
}
