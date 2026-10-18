import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RTooltip,
  XAxis,
  YAxis
} from 'recharts'
import { BadgeCheck, CircleDollarSign, ListOrdered, Package, Users } from 'lucide-react'
import { num } from '@/lib/format'
import { useGetDashboardQuery } from '@/api/opsApi'
import { PageHeader } from '@/components/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const CHART_COLORS = ['#2e5b4e', '#3f7d5c', '#2a3f5c', '#d89a3c', '#c25e3a']
const AXIS = { fontSize: 12, fill: '#6e7a70' }
const TOOLTIP_STYLE = {
  borderRadius: 12,
  border: '1px solid #e4dbc9',
  background: '#fffcf5',
  boxShadow: '0 8px 20px -12px rgba(46,91,78,0.28)',
  fontSize: 12
}

function StatCard({
  label,
  value,
  sub,
  icon: Icon,
  tone
}: {
  label: string
  value: string | number
  sub?: string
  icon: typeof Users
  tone: string
}) {
  return (
    <div className="dashed-card paper-fold relative overflow-hidden p-4">
      <div className="flex items-start justify-between gap-2">
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
        <span
          className="flex size-8 items-center justify-center rounded-lg"
          style={{ backgroundColor: `${tone}1a`, color: tone }}
        >
          <Icon className="size-4" />
        </span>
      </div>
      <div className="mt-2 text-2xl font-bold tabular-nums text-foreground">{value}</div>
      <div className="mt-1 h-4 text-[11px] text-muted-foreground">{sub || '\u00A0'}</div>
    </div>
  )
}

function ChartCard({
  title,
  children,
  extra,
  className
}: {
  title: string
  children: React.ReactNode
  extra?: React.ReactNode
  className?: string
}) {
  return (
    <Card className={cn('overflow-hidden', className)}>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2 text-sm">
          <span className="inline-block h-4 w-1.5 rounded-full bg-accent" />
          {title}
        </CardTitle>
        {extra}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

export default function DashboardPage() {
  const { data, isLoading } = useGetDashboardQuery()
  const d = data

  const statCards = [
    { label: '注册用户', value: num(d?.userTotal), sub: `今日新增 ${num(d?.userTodayNew)}`, icon: Users, tone: '#2e5b4e' },
    { label: '认证用户', value: num(d?.authUserTotal), sub: '已完成校园认证', icon: BadgeCheck, tone: '#3f7d5c' },
    { label: '在售商品', value: num(d?.goodsOnSale), sub: '当前可交易', icon: Package, tone: '#2a3f5c' },
    { label: '订单总数', value: num(d?.orderTotal), sub: '含全部类型', icon: ListOrdered, tone: '#d89a3c' },
    {
      label: '成交总额',
      value: `¥${num(d?.gmvCompleted).toFixed(2)}`,
      sub: '已完成订单合计',
      icon: CircleDollarSign,
      tone: '#c25e3a'
    }
  ]

  const avgHours = d?.reportAvgHandleHours
  const avgText = avgHours == null || !Number.isFinite(Number(avgHours)) ? '—' : Number(avgHours).toFixed(1)
  const todoItems = [
    { label: '待确认订单', value: num(d?.orderWaitConfirm), dot: '#d89a3c' },
    { label: '待办交换', value: num(d?.orderScheduled), dot: '#2a3f5c' },
    { label: '待处理举报', value: num(d?.reportPending), dot: '#b4443a' },
    { label: '举报平均处理时长', value: avgText, unit: '小时', dot: '#3f7d5c' }
  ]

  const dauPoints = Array.isArray(d?.dauTrend30)
    ? d!.dauTrend30!
        .filter((p) => p && typeof p === 'object')
        .map((p) => ({ date: String(p.date || ''), activeUsers: num(p.activeUsers) }))
    : []

  // 后端只返回「本周周一 → 今天」的桶（长度 1~7），按实际长度对齐标签，避免把周三之后都画成 0
  const weekTrend = Array.isArray(d?.ordersWeekTrend) ? d!.ordersWeekTrend!.map((v) => num(v)) : []
  const weekData = weekTrend.map((value, i) => ({ label: WEEK_LABELS[i] || `第 ${i + 1} 天`, value }))

  const creditEntries = d?.creditDistribution && typeof d.creditDistribution === 'object' ? Object.entries(d.creditDistribution) : []
  const creditData = creditEntries.map(([name, value]) => ({ name, value: num(value) }))

  return (
    <div>
      <PageHeader
        title="数据大屏"
        description="平台核心指标、待办与趋势（ADM-08），单接口聚合返回"
        actions={<span className="tape-tag rotate-[-1.5deg]">实时快照</span>}
      />

      {/* 指标卡 */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-[104px] rounded-[var(--radius)]" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
          {statCards.map((card) => (
            <StatCard key={card.label} {...card} />
          ))}
        </div>
      )}

      {/* 待办条 */}
      <Card className="mt-4">
        <CardContent className="flex flex-wrap items-center gap-x-10 gap-y-4">
          {todoItems.map((item) => (
            <div key={item.label} className="flex items-center gap-2.5">
              <span className="size-2 rounded-full" style={{ backgroundColor: item.dot }} />
              <span className="text-sm text-muted-foreground">{item.label}</span>
              <span className="text-xl font-bold tabular-nums text-foreground">{item.value}</span>
              {item.unit ? <span className="-ml-1 text-xs text-muted-foreground">{item.unit}</span> : null}
            </div>
          ))}
        </CardContent>
      </Card>

      {/* 图表区 */}
      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-5">
        <ChartCard
          className="lg:col-span-3"
          title="本周新增订单趋势"
          extra={<span className="text-xs text-muted-foreground">周一 → 今天（后端按天聚合）</span>}
        >
          <div className="h-[320px] w-full">
            {isLoading ? (
              <Skeleton className="size-full rounded-xl" />
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={weekData} margin={{ top: 12, right: 16, left: -12, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4dbc9" vertical={false} />
                  <XAxis dataKey="label" tick={AXIS} axisLine={{ stroke: '#e4dbc9' }} tickLine={false} />
                  <YAxis tick={AXIS} axisLine={false} tickLine={false} allowDecimals={false} />
                  <RTooltip contentStyle={TOOLTIP_STYLE} cursor={{ stroke: '#d8cbb0' }} />
                  <Line
                    type="monotone"
                    dataKey="value"
                    name="新增订单"
                    stroke={CHART_COLORS[0]}
                    strokeWidth={3}
                    dot={{ r: 4, fill: CHART_COLORS[0] }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </ChartCard>

        <ChartCard className="lg:col-span-2" title="用户信用分布">
          <div className="h-[320px] w-full">
            {isLoading ? (
              <Skeleton className="size-full rounded-xl" />
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={creditData.length ? creditData : [{ name: '暂无数据', value: 1 }]}
                    dataKey="value"
                    nameKey="name"
                    innerRadius="45%"
                    outerRadius="70%"
                    paddingAngle={2}
                    stroke="#fffcf5"
                    strokeWidth={2}
                    label={creditData.length ? ({ name, value }) => `${name} ${value}人` : false}
                  >
                    {(creditData.length ? creditData : [{ name: '暂无数据' }]).map((_, i) => (
                      <Cell
                        key={i}
                        fill={creditData.length ? CHART_COLORS[i % CHART_COLORS.length] : '#e5e6eb'}
                      />
                    ))}
                  </Pie>
                  <RTooltip contentStyle={TOOLTIP_STYLE} formatter={(v: number) => `${v} 人`} />
                  {creditData.length ? <Legend verticalAlign="bottom" iconType="circle" /> : null}
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </ChartCard>
      </div>

      <div className="mt-4">
        <ChartCard
          title="近 30 天活跃用户趋势"
          extra={<span className="text-xs text-muted-foreground">DAU（当日有行为用户去重）</span>}
        >
          <div className={cn('w-full', dauPoints.length ? 'h-[280px]' : 'h-[200px]')}>
            {isLoading ? (
              <Skeleton className="size-full rounded-xl" />
            ) : dauPoints.length ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={dauPoints} margin={{ top: 12, right: 16, left: -12, bottom: 0 }}>
                  <defs>
                    <linearGradient id="dauFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={CHART_COLORS[2]} stopOpacity={0.35} />
                      <stop offset="100%" stopColor={CHART_COLORS[2]} stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e4dbc9" vertical={false} />
                  <XAxis
                    dataKey="date"
                    tick={AXIS}
                    axisLine={{ stroke: '#e4dbc9' }}
                    tickLine={false}
                    tickFormatter={(v: string) => String(v).slice(5)}
                    minTickGap={24}
                  />
                  <YAxis tick={AXIS} axisLine={false} tickLine={false} allowDecimals={false} />
                  <RTooltip contentStyle={TOOLTIP_STYLE} />
                  <Area
                    type="monotone"
                    dataKey="activeUsers"
                    name="活跃用户"
                    stroke={CHART_COLORS[2]}
                    strokeWidth={3}
                    fill="url(#dauFill)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">暂无活跃数据</div>
            )}
          </div>
        </ChartCard>
      </div>
    </div>
  )
}
