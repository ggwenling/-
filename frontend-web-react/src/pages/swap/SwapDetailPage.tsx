import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeftRight, Package, Star, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { useAppSelector } from '@/app/hooks'
import { selectIsLoggedIn, selectIsVerified, selectUserId } from '@/features/auth/authSlice'
import { useConfirm } from '@/components/confirm'
import { usePrompt } from '@/components/prompt'
import {
  useAcceptSwapRequestMutation,
  useCloseSwapPostMutation,
  useCreateSwapRequestMutation,
  useGetSwapPostQuery,
  useRejectSwapRequestMutation,
  useRemoveSwapPostMutation
} from '@/api/swapApi'
import { usePageMyGoodsQuery } from '@/api/goodsApi'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { EmptyState } from '@/components/EmptyState'
import { UserCard } from '@/components/UserCard'
import { ReportDialog } from '@/components/ReportDialog'
import { SWAP_REQUEST_STATUS_TEXT, SWAP_STATUS_TEXT } from '@/lib/constants'
import type { ReportTargetType, SwapRequestVO } from '@/api/types'

const requestTone = (status: number) => (status === 0 ? 'warning' : status === 1 ? 'success' : 'info')

/**
 * 交换详情（PRD SWP-01/02/03）：物品对照卡 + 发布者卡 +「我的操作」 + 帖主请求列表 + 发起弹窗 + 举报。
 *
 * 与 Vue 版对齐：
 * - `requestHint` 五级优先文案；`canRequest` 为真才给「发起交换」
 * - 发起弹窗内**懒加载**「我的在售商品」（首次打开才请求，`pageSize=50` 且过滤 `ON_SALE`）
 * - 帖主操作 `canManage = isOwner && status === 'OPEN'`；同意/拒绝/关闭/删除的文案与 `null`/`''` 语义一致
 */
export default function SwapDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const confirm = useConfirm()
  const prompt = usePrompt()

  const isLoggedIn = useAppSelector(selectIsLoggedIn)
  const isVerified = useAppSelector(selectIsVerified)
  const myUserId = useAppSelector(selectUserId)

  const postId = Number(id)
  const { data: post, isLoading } = useGetSwapPostQuery(postId, { skip: !Number.isFinite(postId) || postId <= 0 })

  const [requestOpen, setRequestOpen] = useState(false)
  const [itemDesc, setItemDesc] = useState('')
  const [goodsId, setGoodsId] = useState('')
  const [loadedMyGoods, setLoadedMyGoods] = useState(false)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportType, setReportType] = useState<ReportTargetType>('SWAP')

  const { data: myGoodsPage, isFetching: myGoodsLoading } = usePageMyGoodsQuery(
    { pageNum: 1, pageSize: 50 },
    { skip: !loadedMyGoods }
  )
  const myGoods = (myGoodsPage?.list ?? []).filter((g) => g.status === 'ON_SALE')

  const [createSwapRequest, { isLoading: submitting }] = useCreateSwapRequestMutation()
  const [acceptSwapRequest] = useAcceptSwapRequestMutation()
  const [rejectSwapRequest] = useRejectSwapRequestMutation()
  const [closeSwapPost] = useCloseSwapPostMutation()
  const [removeSwapPost] = useRemoveSwapPostMutation()

  const goLogin = () => navigate(`/login?redirect=${encodeURIComponent(`/swap/${postId}`)}`)
  const myRequest = post?.myRequest ?? null
  const isOwner = !!(post && myUserId && post.publisher?.id === myUserId)
  const canManage = isOwner && post?.status === 'OPEN'
  const hasDiffAmount =
    post?.diffAmount !== null && post?.diffAmount !== undefined && Number(post.diffAmount) > 0

  const requestHint = !isLoggedIn
    ? '登录并完成校园认证后即可发起交换'
    : isOwner
      ? '这是你自己发布的交换帖，不能对自己发起交换'
      : !isVerified
        ? '完成校园认证后才能发起交换'
        : post?.status !== 'OPEN'
          ? '该交换帖已关闭或已成交'
          : '当前账号信用分受限，暂不能发起交换'

  const openRequestDialog = () => {
    setRequestOpen(true)
    if (!loadedMyGoods) setLoadedMyGoods(true)
  }

  const openReport = (type: ReportTargetType) => {
    if (!isLoggedIn) {
      toast.warning('请先登录')
      goLogin()
      return
    }
    setReportType(type)
    setReportOpen(true)
  }

  const doRequest = async () => {
    if (!itemDesc.trim()) {
      toast.warning('请填写你的物品说明')
      return
    }
    try {
      await createSwapRequest({
        postId,
        body: { itemDesc: itemDesc.trim(), goodsId: goodsId ? Number(goodsId) : undefined }
      }).unwrap()
      toast.success('交换请求已提交，等待帖主处理')
      setRequestOpen(false)
      setItemDesc('')
      setGoodsId('')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const doAccept = async (row: SwapRequestVO) => {
    const ok = await confirm({
      title: '同意交换',
      description: `同意「${row.nickname}」的交换请求？同意后将生成交换订单，其余请求自动关闭，双方确认完成即交易成功。`,
      confirmText: '同意并生成订单'
    })
    if (!ok) return
    await acceptSwapRequest(row.id)
    toast.success('已同意，交换订单已生成')
  }

  const doReject = async (row: SwapRequestVO) => {
    const reason = await prompt({
      title: '拒绝交换请求',
      description: '可填写拒绝理由（选填）',
      placeholder: '如：物品不太合适',
      defaultValue: '',
      confirmText: '拒绝'
    })
    if (reason === null) return
    await rejectSwapRequest({ requestId: row.id, reason: reason || undefined })
    toast.success('已拒绝该请求')
  }

  const doClose = async () => {
    const reason = await prompt({
      title: '关闭交换帖',
      description: '关闭后该帖不再接收交换请求，已有待处理请求将失效',
      placeholder: '关闭理由（选填）',
      defaultValue: '',
      confirmText: '关闭'
    })
    if (reason === null) return
    await closeSwapPost({ id: postId, reason: reason || undefined })
    toast.success('已关闭')
  }

  const doRemove = async () => {
    const ok = await confirm({
      title: '删除交换帖',
      description: '删除后帖子不再公开展示，30 天内可以恢复。确定删除？',
      confirmText: '删除',
      tone: 'destructive'
    })
    if (!ok) return
    await removeSwapPost(postId)
    toast.success('已删除')
    navigate('/swap')
  }

  if (isLoading) {
    return (
      <div className="page">
        <Card className="my-6 space-y-3 p-6">
          <Skeleton className="h-6 w-1/2" />
          <Skeleton className="h-20 w-full" />
        </Card>
      </div>
    )
  }

  if (!post) {
    return (
      <div className="page">
        <EmptyState
          description="交换帖不存在或已删除"
          action={
            <Button variant="outline" size="sm" onClick={() => navigate('/swap')}>
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
            <Badge variant="soft">{SWAP_STATUS_TEXT[post.status || ''] || post.status}</Badge>
          ) : null}
          {post.allowDiff === 1 ? (
            <Badge variant="warning">
              可补差价{hasDiffAmount ? ` ¥${Number(post.diffAmount).toFixed(2)}` : ''}
            </Badge>
          ) : null}
          <span className="ml-auto text-[12px] text-muted-foreground">
            {post.categoryName || '未分类'} · {post.createdAt}
          </span>
        </div>

        {/* 双方物品对照 */}
        <div className="mt-4 grid grid-cols-1 items-center gap-3 md:grid-cols-[1fr_auto_1fr]">
          <div className="coupon-card p-4">
            <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
              <Package className="size-3.5" />
              帖主的物品
            </span>
            <p className="mt-2 leading-relaxed whitespace-pre-wrap text-foreground">{post.myItemDesc}</p>
          </div>
          <div className="grid place-items-center gap-1 text-muted-foreground">
            <ArrowLeftRight className="size-7" />
            <span className="text-[11px]">交换</span>
          </div>
          <div className="coupon-card p-4">
            <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
              <Star className="size-3.5" />
              想要的物品
            </span>
            <p className="mt-2 leading-relaxed whitespace-pre-wrap text-foreground">{post.wantItemDesc}</p>
          </div>
        </div>
      </Card>

      <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[1fr_420px]">
        <div className="space-y-3">
          <UserCard user={post.publisher} />
          {!isOwner ? (
            <div className="flex flex-wrap gap-4 px-1">
              <button
                type="button"
                onClick={() => openReport('SWAP')}
                className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground transition hover:text-destructive"
              >
                <TriangleAlert className="size-3.5" />
                举报该交换帖
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

          {myRequest ? (
            <div className="mt-4 space-y-3">
              <div className="coupon-card space-y-2.5 p-3.5">
                <div>
                  <span className="text-[12px] text-muted-foreground">我的物品说明</span>
                  <p className="mt-0.5 text-[13px] text-foreground">{myRequest.itemDesc}</p>
                </div>
                {myRequest.goodsId ? (
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[12px] text-muted-foreground">关联商品</span>
                    <button
                      type="button"
                      className="text-[13px] text-primary hover:underline"
                      onClick={() => navigate(`/goods/${myRequest.goodsId}`)}
                    >
                      {myRequest.goodsTitle || '查看商品'}
                    </button>
                  </div>
                ) : null}
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[12px] text-muted-foreground">状态</span>
                  <Badge variant={requestTone(myRequest.status)}>
                    {SWAP_REQUEST_STATUS_TEXT[myRequest.status] ?? myRequest.status}
                  </Badge>
                </div>
              </div>
              <div className="flex flex-wrap gap-3">
                {myRequest.orderId ? (
                  <Button onClick={() => navigate(`/orders/${myRequest.orderId}`)}>查看交换订单</Button>
                ) : null}
              </div>
            </div>
          ) : !post.canRequest ? (
            <div className="mt-4 space-y-3">
              <div className="rounded-lg border border-[var(--warning)]/25 bg-[var(--warning)]/8 px-3 py-2.5 text-[13px] text-[var(--warning)]">
                {requestHint}
              </div>
              <div className="flex flex-wrap gap-3">
                {!isLoggedIn ? (
                  <Button onClick={goLogin}>登录后发起交换</Button>
                ) : !isVerified ? (
                  <Button onClick={() => navigate('/verify')}>去完成校园认证</Button>
                ) : null}
              </div>
            </div>
          ) : (
            <div className="mt-4 space-y-3">
              <p className="text-[13px] text-muted-foreground">
                填写你的物品说明，也可以关联你在售的商品，帖主同意后即生成交换订单。
              </p>
              <Button size="lg" className="w-full" onClick={openRequestDialog}>
                发起交换
              </Button>
            </div>
          )}
        </Card>
      </div>

      {isOwner ? (
        <Card className="paper-card mt-5 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <b className="text-[15px] text-foreground">收到的交换请求（{(post.requests || []).length}）</b>
            {canManage ? (
              <div className="flex flex-wrap gap-2">
                <Button size="sm" variant="outline" onClick={() => navigate(`/swap/publish?id=${post.id}`)}>
                  编辑
                </Button>
                <Button size="sm" variant="outline" onClick={doClose}>
                  关闭交换
                </Button>
                <Button size="sm" variant="destructive" onClick={doRemove}>
                  删除
                </Button>
              </div>
            ) : null}
          </div>

          {(post.requests || []).length ? (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] text-[13px]">
                <thead>
                  <tr className="border-b border-border text-left text-[12px] text-muted-foreground">
                    <th className="py-2.5 pr-3 font-medium">发起人</th>
                    <th className="py-2.5 pr-3 font-medium">物品说明</th>
                    <th className="py-2.5 pr-3 font-medium">关联商品</th>
                    <th className="py-2.5 pr-3 font-medium">状态</th>
                    <th className="py-2.5 font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(post.requests || []).map((row) => (
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
                      <td className="py-3 pr-3 text-foreground">{row.itemDesc}</td>
                      <td className="py-3 pr-3">
                        {row.goodsId ? (
                          <button
                            type="button"
                            className="text-primary hover:underline"
                            onClick={() => navigate(`/goods/${row.goodsId}`)}
                          >
                            {row.goodsTitle || '查看商品'}
                          </button>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="py-3 pr-3">
                        <Badge variant={requestTone(row.status)}>
                          {SWAP_REQUEST_STATUS_TEXT[row.status] ?? row.status}
                        </Badge>
                      </td>
                      <td className="py-3">
                        {row.status === 0 && canManage ? (
                          <div className="flex gap-2">
                            <Button size="sm" onClick={() => doAccept(row)}>
                              同意
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
            <p className="mt-4 text-[13px] text-muted-foreground">还没有收到交换请求</p>
          )}
        </Card>
      ) : null}

      {/* 发起交换弹窗 */}
      <Dialog open={requestOpen} onOpenChange={setRequestOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>发起交换</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="itemDesc">物品说明</Label>
              <Textarea
                id="itemDesc"
                rows={4}
                maxLength={500}
                placeholder="说明你想拿来交换的物品：名称、成色、入手时间等"
                value={itemDesc}
                onChange={(e) => setItemDesc(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="goodsId">关联商品</Label>
              <select
                id="goodsId"
                value={goodsId}
                onChange={(e) => setGoodsId(e.target.value)}
                className="h-9 w-full rounded-lg border border-input bg-card px-3 text-[13px] text-foreground"
              >
                <option value="">{myGoodsLoading ? '加载中…' : '可选：选择你在售的商品'}</option>
                {myGoods.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.title}
                  </option>
                ))}
              </select>
              <p className="text-[12px] text-muted-foreground">关联后帖主可直接查看商品详情（仅展示在售商品）</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRequestOpen(false)}>
              取消
            </Button>
            <Button disabled={submitting} onClick={doRequest}>
              提交请求
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
