import { useRef, useState } from 'react'
import { EyeOff, ImagePlus, Megaphone, Pencil, Plus, Send, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { TableSkeleton } from '@/components/TableSkeleton'
import { EmptyState } from '@/components/EmptyState'
import { DateTimeText } from '@/components/DateTimeText'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
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
  useCreateBannerMutation,
  useCreateNoticeMutation,
  useDeleteBannerMutation,
  useDeleteNoticeMutation,
  useGetBannersQuery,
  useGetNoticesQuery,
  useOfflineNoticeMutation,
  usePublishNoticeMutation,
  useUpdateBannerMutation,
  useUpdateNoticeMutation
} from '@/api/opsApi'
import { uploadImage, validateImage } from '@/api/upload'
import type { Banner, Notice } from '@/api/types'
import { NOTICE_STATUS_MAPS } from '@/lib/constants'

type TabKey = 'banner' | 'notice'

interface BannerForm {
  id?: number
  title: string
  imageUrl: string
  linkUrl: string
  sort: number
  status: number
}

interface NoticeForm {
  id?: number
  title: string
  content: string
}

export default function OperationConfigPage() {
  const confirm = useConfirm()
  const [tab, setTab] = useState<TabKey>('banner')

  // ---------- 轮播 ----------
  const { data: banners, isFetching: bannerLoading } = useGetBannersQuery()
  const [createBanner, { isLoading: creatingBanner }] = useCreateBannerMutation()
  const [updateBanner, { isLoading: updatingBanner }] = useUpdateBannerMutation()
  const [deleteBanner] = useDeleteBannerMutation()

  const [bannerDialogOpen, setBannerDialogOpen] = useState(false)
  const [bannerForm, setBannerForm] = useState<BannerForm>({ title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 })
  const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  // ---------- 公告 ----------
  const { data: notices, isFetching: noticeLoading } = useGetNoticesQuery()
  const [createNotice, { isLoading: creatingNotice }] = useCreateNoticeMutation()
  const [updateNotice, { isLoading: updatingNotice }] = useUpdateNoticeMutation()
  const [publishNotice] = usePublishNoticeMutation()
  const [offlineNotice] = useOfflineNoticeMutation()
  const [deleteNotice] = useDeleteNoticeMutation()

  const [noticeDialogOpen, setNoticeDialogOpen] = useState(false)
  const [noticeForm, setNoticeForm] = useState<NoticeForm>({ title: '', content: '' })

  const bannerRows = banners || []
  const noticeRows = notices || []

  const openCreateBanner = () => {
    setBannerForm({ title: '', imageUrl: '', linkUrl: '', sort: 0, status: 1 })
    setBannerDialogOpen(true)
  }

  const openEditBanner = (row: Banner) => {
    setBannerForm({
      id: row.id,
      title: row.title || '',
      imageUrl: row.imageUrl || '',
      linkUrl: row.linkUrl || '',
      sort: row.sort ?? 0,
      status: row.status ?? 1
    })
    setBannerDialogOpen(true)
  }

  const onPickImage = async (file?: File) => {
    if (!file) return
    const invalid = validateImage(file)
    if (invalid) {
      toast.warning(invalid)
      return
    }
    setUploading(true)
    try {
      const res = await uploadImage(file)
      setBannerForm((prev) => ({ ...prev, imageUrl: res.url }))
      toast.success('图片上传成功')
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '图片上传失败')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const submitBanner = async () => {
    if (!bannerForm.imageUrl.trim()) {
      toast.warning('请上传轮播图片')
      return
    }
    try {
      const payload = {
        title: bannerForm.title.trim(),
        imageUrl: bannerForm.imageUrl.trim(),
        linkUrl: bannerForm.linkUrl.trim() || null,
        sort: Number(bannerForm.sort) || 0
      }
      if (bannerForm.id != null) {
        await updateBanner({ id: bannerForm.id, ...payload, status: bannerForm.status }).unwrap()
        toast.success('轮播图已更新')
      } else {
        await createBanner(payload).unwrap()
        toast.success('轮播图已创建')
      }
      setBannerDialogOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const removeBanner = async (row: Banner) => {
    const ok = await confirm({
      title: '删除轮播图',
      description: `确认删除「${row.title || `#${row.id}`}」？`,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await deleteBanner(row.id).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const openCreateNotice = () => {
    setNoticeForm({ title: '', content: '' })
    setNoticeDialogOpen(true)
  }

  const openEditNotice = (row: Notice) => {
    setNoticeForm({ id: row.id, title: row.title || '', content: row.content || '' })
    setNoticeDialogOpen(true)
  }

  const submitNotice = async () => {
    if (!noticeForm.title.trim()) {
      toast.warning('请填写公告标题')
      return
    }
    if (!noticeForm.content.trim()) {
      toast.warning('请填写公告内容')
      return
    }
    try {
      if (noticeForm.id != null) {
        await updateNotice({ id: noticeForm.id, title: noticeForm.title.trim(), content: noticeForm.content.trim() }).unwrap()
        toast.success('公告已更新')
      } else {
        await createNotice({ title: noticeForm.title.trim(), content: noticeForm.content.trim() }).unwrap()
        toast.success('公告已创建（草稿）')
      }
      setNoticeDialogOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onPublish = async (row: Notice) => {
    const ok = await confirm({
      title: '发布公告',
      description: `确认发布「${row.title || `#${row.id}`}」？发布后前台将展示该公告。`,
      confirmText: '确认发布'
    })
    if (!ok) return
    try {
      await publishNotice(row.id).unwrap()
      toast.success('已发布')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onOffline = async (row: Notice) => {
    const ok = await confirm({
      title: '下线公告',
      description: `确认下线「${row.title || `#${row.id}`}」？`,
      confirmText: '确认下线',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await offlineNotice(row.id).unwrap()
      toast.success('已下线')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onDeleteNotice = async (row: Notice) => {
    const ok = await confirm({
      title: '删除公告',
      description: `确认删除「${row.title || `#${row.id}`}」？删除后不可恢复。`,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await deleteNotice(row.id).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="运营配置"
        description="首页轮播图与平台公告管理（ADM-07，超级管理员 / 运营员）"
        actions={
          <Button onClick={tab === 'banner' ? openCreateBanner : openCreateNotice}>
            <Plus className="size-4" />
            {tab === 'banner' ? '新建轮播图' : '新建公告'}
          </Button>
        }
      />

      <Card className="p-4">
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
          <TabsList>
            <TabsTrigger value="banner">轮播图</TabsTrigger>
            <TabsTrigger value="notice">平台公告</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="mt-4">
          {tab === 'banner' ? (
            bannerLoading && !bannerRows.length ? (
              <TableSkeleton />
            ) : bannerRows.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[72px]">ID</TableHead>
                    <TableHead className="w-[120px]">预览</TableHead>
                    <TableHead>标题</TableHead>
                    <TableHead className="w-[200px]">跳转链接</TableHead>
                    <TableHead className="w-[80px]">排序</TableHead>
                    <TableHead className="w-[100px]">状态</TableHead>
                    <TableHead className="w-[170px] text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {bannerRows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                      <TableCell>
                        {row.imageUrl ? (
                          <a href={row.imageUrl} target="_blank" rel="noreferrer">
                            <img
                              src={row.imageUrl}
                              alt={row.title || 'banner'}
                              className="h-12 w-20 rounded-md border border-border object-cover"
                            />
                          </a>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                      <TableCell className="max-w-[220px] truncate font-medium">{row.title || '—'}</TableCell>
                      <TableCell className="max-w-[200px] truncate text-xs text-muted-foreground">{row.linkUrl || '—'}</TableCell>
                      <TableCell className="tabular-nums text-muted-foreground">{row.sort ?? 0}</TableCell>
                      <TableCell>
                        {row.status === 0 ? (
                          <StatusBadge label="已隐藏" tone="soft" />
                        ) : (
                          <StatusBadge label="展示中" tone="success" />
                        )}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          <Button variant="ghost" size="sm" onClick={() => openEditBanner(row)}>
                            <Pencil className="size-3.5" />
                            编辑
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => removeBanner(row)}
                          >
                            <Trash2 className="size-3.5" />
                            删除
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            ) : (
              <EmptyState title="暂无轮播图" description="点击右上角新建轮播图" />
            )
          ) : noticeLoading && !noticeRows.length ? (
            <TableSkeleton />
          ) : noticeRows.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[72px]">ID</TableHead>
                  <TableHead>标题</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[150px]">发布时间</TableHead>
                  <TableHead className="w-[150px]">创建时间</TableHead>
                  <TableHead className="w-[290px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {noticeRows.map((row) => {
                  const meta = NOTICE_STATUS_MAPS[row.status ?? 0] ?? { label: '未知', badge: 'soft' }
                  return (
                    <TableRow key={row.id}>
                      <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                      <TableCell className="max-w-[320px] truncate font-medium" title={row.title || ''}>
                        {row.title || '—'}
                      </TableCell>
                      <TableCell>
                        <StatusBadge label={meta.label} tone={meta.badge} />
                      </TableCell>
                      <TableCell>
                        <DateTimeText value={row.publishedAt} className="text-muted-foreground" />
                      </TableCell>
                      <TableCell>
                        <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          <Button variant="ghost" size="sm" onClick={() => openEditNotice(row)}>
                            <Pencil className="size-3.5" />
                            编辑
                          </Button>
                          {/* 后端 publish 仅接受 草稿(0)→已发布(1)，offline 仅接受 已发布(1)→已下线(2) */}
                          {row.status === 0 ? (
                            <Button variant="outline" size="sm" onClick={() => onPublish(row)}>
                              <Send className="size-3.5" />
                              发布
                            </Button>
                          ) : row.status === 1 ? (
                            <Button variant="outline" size="sm" onClick={() => onOffline(row)}>
                              <EyeOff className="size-3.5" />
                              下线
                            </Button>
                          ) : null}
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => onDeleteNotice(row)}
                          >
                            <Trash2 className="size-3.5" />
                            删除
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          ) : (
            <EmptyState title="暂无公告" description="点击右上角新建公告" />
          )}
        </div>
      </Card>

      {/* 轮播弹窗 */}
      <Dialog open={bannerDialogOpen} onOpenChange={(open) => !open && setBannerDialogOpen(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{bannerForm.id != null ? '编辑轮播图' : '新建轮播图'}</DialogTitle>
            <DialogDescription>图片支持 jpg / png / webp，单张不超过 5MB。</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label className="text-xs text-muted-foreground">轮播图片</Label>
              <div className="flex items-start gap-3">
                <div className="flex h-[72px] w-[120px] items-center justify-center overflow-hidden rounded-lg border border-dashed border-[#d8cbb0] bg-secondary/40">
                  {bannerForm.imageUrl ? (
                    <img src={bannerForm.imageUrl} alt="预览" className="size-full object-cover" />
                  ) : (
                    <ImagePlus className="size-5 text-muted-foreground" />
                  )}
                </div>
                <div className="space-y-2">
                  <Button variant="outline" size="sm" disabled={uploading} onClick={() => fileRef.current?.click()}>
                    <ImagePlus className="size-3.5" />
                    {uploading ? '上传中…' : '上传图片'}
                  </Button>
                  <Input
                    value={bannerForm.imageUrl}
                    placeholder="或粘贴图片地址"
                    className="w-[280px]"
                    onChange={(e) => setBannerForm({ ...bannerForm, imageUrl: e.target.value })}
                  />
                </div>
              </div>
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={(e) => onPickImage(e.target.files?.[0])}
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">标题</Label>
              <Input
                value={bannerForm.title}
                placeholder="轮播标题（可选）"
                onChange={(e) => setBannerForm({ ...bannerForm, title: e.target.value })}
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">跳转链接</Label>
              <Input
                value={bannerForm.linkUrl}
                placeholder="例如 /goods/123（可选）"
                onChange={(e) => setBannerForm({ ...bannerForm, linkUrl: e.target.value })}
              />
            </div>

            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">排序（越小越靠前）</Label>
              <Input
                type="number"
                value={bannerForm.sort}
                onChange={(e) => setBannerForm({ ...bannerForm, sort: Number(e.target.value) })}
              />
            </div>

            {bannerForm.id != null ? (
              <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
                <div>
                  <div className="text-sm font-medium">前台展示</div>
                  <div className="text-[11px] text-muted-foreground">关闭后该轮播图不对外展示</div>
                </div>
                <Switch
                  checked={bannerForm.status === 1}
                  onCheckedChange={(checked) => setBannerForm({ ...bannerForm, status: checked ? 1 : 0 })}
                />
              </div>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setBannerDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={creatingBanner || updatingBanner || uploading} onClick={submitBanner}>
              {bannerForm.id != null ? '保存修改' : '创建轮播图'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 公告弹窗 */}
      <Dialog open={noticeDialogOpen} onOpenChange={(open) => !open && setNoticeDialogOpen(false)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Megaphone className="size-4 text-accent" />
              {noticeForm.id != null ? '编辑公告' : '新建公告'}
            </DialogTitle>
            <DialogDescription>新建后为草稿状态，需手动发布才会在前台展示。</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">公告标题</Label>
              <Input
                value={noticeForm.title}
                placeholder="请输入公告标题"
                onChange={(e) => setNoticeForm({ ...noticeForm, title: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">公告内容</Label>
              <Textarea
                rows={6}
                value={noticeForm.content}
                placeholder="请输入公告内容"
                onChange={(e) => setNoticeForm({ ...noticeForm, content: e.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setNoticeDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={creatingNotice || updatingNotice} onClick={submitNotice}>
              {noticeForm.id != null ? '保存修改' : '创建公告'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
