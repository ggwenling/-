import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn, selectIsVerified, selectUserId } from '@/features/auth/authSlice'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import {
  useAcceptOfferMutation,
  useCloseWantPostMutation,
  useCreateOfferMutation,
  useGetWantPostQuery,
  useRejectOfferMutation,
  useRemoveWantPostMutation,
  useWithdrawOfferMutation
} from '@/api/wantApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { EmptyState } from '@/components/EmptyState'
import { PriceText } from '@/components/PriceText'
import { UserCard } from '@/components/UserCard'
import { ReportDialog } from '@/components/ReportDialog'
import { OFFER_STATUS_TEXT, WANT_STATUS_TEXT } from '@/lib/constants'
import type { OfferVO, ReportTargetType } from '@/api/types'

const offerTone = (status: number) => (status === 0 ? 'warning' : status === 1 ? 'success' : 'soft')

/**
 * 求购详情（PRD REQ-02/03/04）：描述卡 + 发布者卡 +「我的操作」三态 + 帖主应约列表 + 应约弹窗 + 举报。
 *
 * 与 Vue 版逐条对齐：
 * - `offerHint` 五级优先文案、`el-alert` 的 info/warning 判定
 * - 帖主操作按钮仅 `canManage = isOwner && status === 'OPEN'` 时显示
 * - 应约表格操作列：`status===0 && canManage` → 接受/拒绝；否则有订单 → 查看订单；否则「—」
 * - **`promptAction` 的 `null`（取消）/ `''`（空理由仍提交）语义**：`reason === null` 才中止
 */
export default function WantDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const confirm = useConfirm()
  const prompt = usePrompt()

  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const isVerified = useAppSelector(selectIsVerified)
  const myUserId = useAppSelector(selectUserId)

  const postId = Number(id)
  const { data: post, isLoading } = useGetWantPostQuery(postId, { skip: !Number.isFinite(postId) || postId <= 0 })

  const [offerOpen, setOfferOpen] = useState(false)
  const [price, setPrice] = useState('')
  const [message, setMessage] = useState('')
  const [reportOpen, setReportOpen] = useState(false)
  const [reportType, setReportType] = useState<ReportTargetType>('WANT')

  const [createOffer, { isLoading: offering }] = useCreateOfferMutation()
  const [withdrawOffer] = useWithdrawOfferMutation()
  const [acceptOffer] = useAcceptOfferMutation()
  const [rejectOffer] = useRejectOfferMutation()
  const [closeWantPost] = useCloseWantPostMutation()
  const [removeWantPost] = useRemoveWantPostMutation()

  const goLogin = () => navigate(`/login?redirect=${encodeURIComponent(`/want/${postId}`)}`)
  const myOffer = post?.myOffer ?? null
  const isOwner = !!(post && myUserId && post.publisher?.id === myUserId)
  const canManage = isOwner && post?.status === 'OPEN'
  const canOfferAction = post?.canOffer === true
  const hasBudget = post?.budget !== null && post?.budget !== undefined

  const offerHint = !isLoggedIn
    ? '登录并完成校园认证后即可应约'
    : isOwner
      ? '这是你自己发布的求购帖，不能应约'
      : !isVerified
        ? '完成校园认证后才能应约'
        : post?.status !== 'OPEN'
          ? '该求购帖已关闭或已成交，不能再应约'
          : '当前账号信用分受限，暂不能应约'

  const openReport = (type: ReportTargetType) => {
    if (!isLoggedIn) {
      toast.warning('请先登录')
      goLogin()
      return
    }
    setReportType(type)
    setReportOpen(true)
  }

  const doOffer = async () => {
    const priceText = price.trim()
    if (priceText === '') {
      toast.warning('请填写报价')
      return
    }
    try {
      await createOffer({ postId, body: { price: Number(priceText), message: message || undefined } }).unwrap()
      toast.success('应约已提交，等待求购者处理')
      setOfferOpen(false)
      setPrice('')
      setMessage('')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const doWithdraw = async () => {
    if (!myOffer) return
    const ok = await confirm({
      title: '撤回应约',
      description: '确定撤回这条应约吗？撤回后可再次提交。',
      confirmText: '撤回'
    })
    if (!ok) return
    await withdrawOffer(myOffer.id)
    toast.success('已撤回')
  }

  const doAccept = async (row: OfferVO) => {
    const ok = await confirm({
      title: '接受应约',
      description: `接受「${row.nickname}」的报价 ¥${Number(row.price).toFixed(2)}？接受后将生成订单，其余应约自动关闭。`,
      confirmText: '接受并生成订单'
    })
    if (!ok) return
    await acceptOffer(row.id)
    toast.success('已接受，订单已生成，请到订单详情查看')
  }

  const doReject = async (row: OfferVO) => {
    const reason = await prompt({
      title: '拒绝应约',
      description: '可填写拒绝理由（选填）',
      placeholder: '如：价格不合适 / 已找到更合适的',
      defaultValue: '',
      confirmText: '拒绝'
    })
    if (reason === null) return
    await rejectOffer({ offerId: row.id, reason: reason || undefined })
    toast.success('已拒绝该应约')
  }

  const doClose = async () => {
    const reason = await prompt({
      title: '关闭求购帖',
      description: '关闭后该帖不再接收应约，已有待处理应约将失效',
      placeholder: '关闭理由（选填）',
      defaultValue: '',
      confirmText: '关闭'
    })
    if (reason === null) return
    await closeWantPost({ id: postId, reason: reason || undefined })
    toast.success('已关闭')
  }

  const doRemove = async () => {
    const ok = await confirm({
      title: '删除求购帖',
      description: '删除后帖子不再公开展示，30 天内可以恢复。确定删除？',
      confirmText: '删除',
      tone: 'destructive'
    })
    if (!ok) return
    await removeWantPost(postId)
    toast.success('已删除')
    navigate('/want')
  }

  if (isLoading) {
    return (
      <div className="page">
        <Card className="my-6 space-y-3 p-6">
          <Skeleton className="h-6 w-1/2" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-4/5" />
        </Card>
      </div>
    )
  }

  if (!post) {
    return (
      <div className="page">
        <EmptyState
          description="求购帖不存在或已删除"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/want')}>
              返回广场
            </Button>
          }
        />
      </div>
    )
  }

  const reportTarget =
    reportType === 'USER'
      ? { id: post.publisher?.id ?? null, title: post.publisher?.nickname || '' }
      : { id: post.id, title: post.title || '' }

  return (
    <div className="page">
      <Card className="paper-card p-6">
        <div className="flex flex-wrap items-center gap-3">
          <h2 className="m-0 text-xl font-bold text-foreground">{post.title}</h2>
          {post.status !== 'OPEN' ? (
            <Badge variant="soft">{WANT_STATUS_TEXT[post.status || ''] || post.status}</Badge>
          ) : null}
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className="text-[12px] text-muted-foreground">心理价</span>
          {hasBudget ? <PriceText value={post.budget} size="large" /> : <span className="text-[15px] text-muted-foreground">价格面议</span>}
          <Badge variant="outline">{post.categoryName || '未分类'}</Badge>
          <span className="ml-auto text-[12px] text-muted-foreground">
            {post.offerCount ?? 0} 条应约 · {post.createdAt}
          </span>
        </div>
        <p className="mt-4 border-t border-dashed border-border pt-4 leading-[1.8] whitespace-pre-wrap text-foreground">
          {post.description}
        </p>
      </Card>

      <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1fr_420px]">
        <div className="space-y-3">
          <UserCard user={post.publisher} />
          {!isOwner ? (
            <div className="flex flex-wrap gap-4 px-1">
              <button
                type="button"
                onClick={() => openReport('WANT')}
                className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground transition hover:text-destructive"
              >
                <TriangleAlert className="size-3.5" />
                举报该求购帖
              </button>
              <button
                type="button"
                onClick={() => openReport('USER')}
                className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground transition hover:text-destructive"
              >
                <TriangleAlert className="size-3.5" />
                举报该用户
              </button>
            </div>
          ) : null}
        </div>

        <Card className="paper-card p-5">
          <b className="text-[15px] text-foreground">我的操作</b>

          {myOffer ? (
            <div className="mt-4 space-y-3">
              <div className="coupon-card space-y-2.5 p-3.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[12px] text-muted-foreground">我的报价</span>
                  <PriceText value={myOffer.price} />
                </div>
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[12px] text-muted-foreground">状态</span>
                  <Badge variant={offerTone(myOffer.status)}>{OFFER_STATUS_TEXT[myOffer.status] ?? myOffer.status}</Badge>
                </div>
                {myOffer.message ? (
                  <div>
                    <span className="text-[12px] text-muted-foreground">留言</span>
                    <p className="mt-0.5 text-[13px] text-foreground">{myOffer.message}</p>
                  </div>
                ) : null}
              </div>
              <div className="flex flex-wrap gap-3">
                {myOffer.status === 0 ? (
                  <Button variant="outline" onClick={doWithdraw}>
                    撤回应约
                  </Button>
                ) : null}
                {myOffer.orderId ? <Button onClick={() => navigate(`/orders/${myOffer.orderId}`)}>查看订单</Button> : null}
              </div>
            </div>
          ) : !canOfferAction ? (
            <div className="mt-4 space-y-3">
              <div
                className={
                  post.canOffer === false && !isLoggedIn
                    ? 'rounded-lg border border-[var(--info)]/25 bg-[var(--info)]/8 px-3 py-2.5 text-[13px] text-[var(--info)]'
                    : 'rounded-lg border border-[var(--warning)]/25 bg-[var(--warning)]/8 px-3 py-2.5 text-[13px] text-[var(--warning)]'
                }
              >
                {offerHint}
              </div>
              <div className="flex flex-wrap gap-3">
                {!isLoggedIn ? (
                  <Button onClick={goLogin}>登录后应约</Button>
                ) : !isVerified ? (
                  <Button onClick={() => navigate('/verify')}>去完成校园认证</Button>
                ) : null}
              </div>
            </div>
          ) : (
            <div className="mt-4 space-y-3">
              <p className="text-[13px] text-muted-foreground">填写你的报价和留言，求购者会选择一条应约成交。</p>
              <Button size="lg" className="w-full" onClick={() => setOfferOpen(true)}>
                我要应约
              </Button>
            </div>
          )}
        </Card>
      </div>

      {isOwner ? (
        <Card className="paper-card mt-5 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <b className="text-[15px] text-foreground">收到的应约（{(post.offers || []).length}）</b>
            {canManage ? (
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="outline" onClick={() => navigate(`/want/publish?id=${post.id}`)}>
                  编辑
                </Button>
                <Button size="sm" variant="outline" onClick={doClose}>
                  关闭求购
                </Button>
                <Button size="sm" variant="destructive" onClick={doRemove}>
                  删除
                </Button>
              </div>
            ) : null}
          </div>

          {(post.offers || []).length ? (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[720px] text-[13px]">
                <thead>
                  <tr className="border-b border-border text-left text-[12px] text-muted-foreground">
                    <th className="py-2.5 pr-3 font-medium">应约人</th>
                    <th className="py-2.5 pr-3 font-medium">报价</th>
                    <th className="py-2.5 pr-3 font-medium">留言</th>
                    <th className="py-2.5 pr-3 font-medium">状态</th>
                    <th className="py-2.5 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(post.offers || []).map((row) => (
                    <tr key={row.id} className="border-b border-dashed border-border last:border-0">
                      <td className="py-3 pr-3">
                        <div className="flex items-center gap-2">
                          {row.avatar ? (
                            <img src={row.avatar} alt="" className="size-6.5 rounded-full object-cover" />
                          ) : (
                            <span className="grid size-6.5 place-items-center rounded-full bg-secondary text-[11px] text-primary">
                              {(row.nickname || '同')[0]}
                            </span>
                          )}
                          <span className="text-foreground">{row.nickname || '校园用户'}</span>
                        </div>
                      </td>
                      <td className="py-3 pr-3">
                        <PriceText value={row.price} />
                      </td>
                      <td className="py-3 pr-3 text-muted-foreground">{row.message || '—'}</td>
                      <td className="py-3 pr-3">
                        <Badge variant={offerTone(row.status)}>{OFFER_STATUS_TEXT[row.status] ?? row.status}</Badge>
                      </td>
                      <td className="py-3">
                        {row.status === 0 && canManage ? (
                          <div className="flex gap-2">
                            <Button size="sm" onClick={() => doAccept(row)}>
                              接受
                            </Button>
                            <Button size="sm" variant="outline" onClick={() => doReject(row)}>
                              拒绝
                            </Button>
                          </div>
                        ) : row.orderId ? (
                          <button
                            type="button"
                            className="text-primary hover:underline"
                            onClick={() => navigate(`/orders/${row.orderId}`)}
                          >
                            查看订单
                          </button>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="mt-4 text-[13px] text-muted-foreground">还没有收到应约，试试分享给同学</p>
          )}
        </Card>
      ) : null}

      {/* 应约弹窗 */}
      <Dialog open={offerOpen} onOpenChange={setOfferOpen}>
        <DialogContent className="sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>提交应约</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="offerPrice">报价</Label>
              <div className="flex items-center gap-3">
                <Input
                  id="offerPrice"
                  type="number"
                  min={0}
                  step={1}
                  className="w-[180px]"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                />
                <span className="text-[12px] text-muted-foreground">元</span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="offerMessage">留言</Label>
              <Textarea
                id="offerMessage"
                rows={3}
                maxLength={200}
                placeholder="说明成色、可面交时间地点等"
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOfferOpen(false)}>
              取消
            </Button>
            <Button disabled={offering} onClick={doOffer}>
              提交应约
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ReportDialog
        open={reportOpen}
        onOpenChange={setReportOpen}
        targetType={reportType}
        targetId={reportTarget.id}
        targetTitle={reportTarget.title}
      />
    </div>
  )
}
