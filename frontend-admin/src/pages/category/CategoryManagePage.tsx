import { useMemo, useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
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
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
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
  useCreateCategoryMutation,
  useCreateTagMutation,
  useDeleteCategoryMutation,
  useDeleteTagMutation,
  useGetCategoriesQuery,
  useGetTagsQuery,
  useUpdateCategoryMutation,
  useUpdateTagMutation
} from '@/api/catalogApi'
import type { Category, Tag } from '@/api/types'

type TabKey = 'category' | 'tag'

/** 后端 Category/Tag.status：0=启用(STATUS_ENABLED) / 1=禁用(STATUS_DISABLED) */
const CATALOG_STATUS_ENABLED = 0
const CATALOG_STATUS_DISABLED = 1

interface CategoryForm {
  id?: number
  parentId: number
  name: string
  icon: string
  sort: number
  status: number
}

interface TagForm {
  id?: number
  name: string
  sort: number
  status: number
}

export default function CategoryManagePage() {
  const confirm = useConfirm()
  const [tab, setTab] = useState<TabKey>('category')

  // ---------- 分类 ----------
  const { data: categories, isFetching: catLoading } = useGetCategoriesQuery()
  const [createCategory, { isLoading: creatingCat }] = useCreateCategoryMutation()
  const [updateCategory, { isLoading: updatingCat }] = useUpdateCategoryMutation()
  const [deleteCategory] = useDeleteCategoryMutation()

  const [catDialogOpen, setCatDialogOpen] = useState(false)
  const [catForm, setCatForm] = useState<CategoryForm>({ parentId: 0, name: '', icon: '', sort: 0, status: CATALOG_STATUS_ENABLED })

  // ---------- 标签 ----------
  const { data: tags, isFetching: tagLoading } = useGetTagsQuery()
  const [createTag, { isLoading: creatingTag }] = useCreateTagMutation()
  const [updateTag, { isLoading: updatingTag }] = useUpdateTagMutation()
  const [deleteTag] = useDeleteTagMutation()

  const [tagDialogOpen, setTagDialogOpen] = useState(false)
  const [tagForm, setTagForm] = useState<TagForm>({ name: '', sort: 0, status: CATALOG_STATUS_ENABLED })

  const catNameMap = useMemo(() => {
    const map = new Map<number, string>()
    ;(categories || []).forEach((c) => map.set(c.id, c.name))
    return map
  }, [categories])

  const parentOptions = useMemo(
    () => (categories || []).filter((c) => !c.parentId || c.parentId === 0),
    [categories]
  )

  const openCreateCategory = () => {
    setCatForm({ parentId: 0, name: '', icon: '', sort: 0, status: CATALOG_STATUS_ENABLED })
    setCatDialogOpen(true)
  }

  const openEditCategory = (row: Category) => {
    setCatForm({
      id: row.id,
      parentId: row.parentId ?? 0,
      name: row.name,
      icon: row.icon || '',
      sort: row.sort ?? 0,
      status: row.status ?? CATALOG_STATUS_ENABLED
    })
    setCatDialogOpen(true)
  }

  const submitCategory = async () => {
    if (!catForm.name.trim()) {
      toast.warning('请填写分类名称')
      return
    }
    try {
      if (catForm.id != null) {
        await updateCategory({
          id: catForm.id,
          name: catForm.name.trim(),
          icon: catForm.icon.trim() || null,
          sort: Number(catForm.sort) || 0,
          status: catForm.status
        }).unwrap()
        toast.success('分类已更新')
      } else {
        await createCategory({
          parentId: Number(catForm.parentId) || 0,
          name: catForm.name.trim(),
          sort: Number(catForm.sort) || 0
        }).unwrap()
        toast.success('分类已创建')
      }
      setCatDialogOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const removeCategory = async (row: Category) => {
    const ok = await confirm({
      title: '删除分类',
      description: `确认删除分类「${row.name}」？若该分类下仍有内容或子分类，后端将拒绝删除。`,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await deleteCategory(row.id).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const openCreateTag = () => {
    setTagForm({ name: '', sort: 0, status: CATALOG_STATUS_ENABLED })
    setTagDialogOpen(true)
  }

  const openEditTag = (row: Tag) => {
    setTagForm({ id: row.id, name: row.name, sort: row.sort ?? 0, status: row.status ?? CATALOG_STATUS_ENABLED })
    setTagDialogOpen(true)
  }

  const submitTag = async () => {
    if (!tagForm.name.trim()) {
      toast.warning('请填写标签名称')
      return
    }
    try {
      if (tagForm.id != null) {
        await updateTag({
          id: tagForm.id,
          name: tagForm.name.trim(),
          sort: Number(tagForm.sort) || 0,
          status: tagForm.status
        }).unwrap()
        toast.success('标签已更新')
      } else {
        await createTag({ name: tagForm.name.trim(), sort: Number(tagForm.sort) || 0 }).unwrap()
        toast.success('标签已创建')
      }
      setTagDialogOpen(false)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const removeTag = async (row: Tag) => {
    const ok = await confirm({
      title: '删除标签',
      description: `确认删除标签「${row.name}」？`,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await deleteTag(row.id).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const catRows = categories || []
  const tagRows = tags || []

  return (
    <div>
      <PageHeader
        title="分类管理"
        description="商品分类树与标签维护（ADM-06，超级管理员 / 运营员）"
        actions={
          <Button onClick={tab === 'category' ? openCreateCategory : openCreateTag}>
            <Plus className="size-4" />
            {tab === 'category' ? '新建分类' : '新建标签'}
          </Button>
        }
      />

      <Card className="p-4">
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
          <TabsList>
            <TabsTrigger value="category">分类管理</TabsTrigger>
            <TabsTrigger value="tag">标签管理</TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="mt-4">
          {tab === 'category' ? (
            catLoading && !catRows.length ? (
              <TableSkeleton />
            ) : catRows.length ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[72px]">ID</TableHead>
                    <TableHead className="w-[160px]">名称</TableHead>
                    <TableHead className="w-[140px]">父级分类</TableHead>
                    <TableHead className="w-[90px]">排序</TableHead>
                    <TableHead className="w-[110px]">状态</TableHead>
                    <TableHead className="w-[150px]">创建时间</TableHead>
                    <TableHead className="w-[170px] text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {catRows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                      <TableCell className="font-medium">
                        <span className="flex items-center gap-2">
                          {row.icon ? <span>{row.icon}</span> : null}
                          {row.name}
                        </span>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {row.parentId && row.parentId !== 0 ? catNameMap.get(row.parentId) || `#${row.parentId}` : '顶级分类'}
                      </TableCell>
                      <TableCell className="tabular-nums text-muted-foreground">{row.sort ?? 0}</TableCell>
                      <TableCell>
                        {row.status === CATALOG_STATUS_DISABLED ? (
                          <StatusBadge label="已禁用" tone="soft" />
                        ) : (
                          <StatusBadge label="启用中" tone="success" />
                        )}
                      </TableCell>
                      <TableCell>
                        <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          <Button variant="ghost" size="sm" onClick={() => openEditCategory(row)}>
                            <Pencil className="size-3.5" />
                            编辑
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => removeCategory(row)}
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
              <EmptyState title="暂无分类" description="点击右上角新建分类" />
            )
          ) : tagLoading && !tagRows.length ? (
            <TableSkeleton />
          ) : tagRows.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[72px]">ID</TableHead>
                  <TableHead className="w-[220px]">标签名称</TableHead>
                  <TableHead className="w-[90px]">排序</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[150px]">创建时间</TableHead>
                  <TableHead className="w-[170px] text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tagRows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                    <TableCell className="font-medium">
                      <StatusBadge label={row.name} tone="soft" />
                    </TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">{row.sort ?? 0}</TableCell>
                    <TableCell>
                      {row.status === CATALOG_STATUS_DISABLED ? (
                        <StatusBadge label="已禁用" tone="soft" />
                      ) : (
                        <StatusBadge label="启用中" tone="success" />
                      )}
                    </TableCell>
                    <TableCell>
                      <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1.5">
                        <Button variant="ghost" size="sm" onClick={() => openEditTag(row)}>
                          <Pencil className="size-3.5" />
                          编辑
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => removeTag(row)}
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
            <EmptyState title="暂无标签" description="点击右上角新建标签" />
          )}
        </div>
      </Card>

      {/* 分类弹窗 */}
      <Dialog open={catDialogOpen} onOpenChange={(open) => !open && setCatDialogOpen(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{catForm.id != null ? '编辑分类' : '新建分类'}</DialogTitle>
            <DialogDescription>分类用于前台商品归类，支持二级结构。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">分类名称</Label>
              <Input
                value={catForm.name}
                placeholder="例如：教材书籍"
                onChange={(e) => setCatForm({ ...catForm, name: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">父级分类</Label>
              <Select
                value={String(catForm.parentId)}
                onValueChange={(v) => setCatForm({ ...catForm, parentId: Number(v) })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="0">顶级分类</SelectItem>
                  {parentOptions
                    .filter((c) => c.id !== catForm.id)
                    .map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.name}
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">排序（越小越靠前）</Label>
                <Input
                  type="number"
                  value={catForm.sort}
                  onChange={(e) => setCatForm({ ...catForm, sort: Number(e.target.value) })}
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">图标（可选，emoji/URL）</Label>
                <Input
                  value={catForm.icon}
                  placeholder="📚"
                  onChange={(e) => setCatForm({ ...catForm, icon: e.target.value })}
                />
              </div>
            </div>
            {catForm.id != null ? (
              <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
                <div>
                  <div className="text-sm font-medium">启用分类</div>
                  <div className="text-[11px] text-muted-foreground">禁用后前台不再展示该分类</div>
                </div>
                <Switch
                  checked={catForm.status === CATALOG_STATUS_ENABLED}
                  onCheckedChange={(checked) => setCatForm({ ...catForm, status: checked ? CATALOG_STATUS_ENABLED : CATALOG_STATUS_DISABLED })}
                />
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCatDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={creatingCat || updatingCat} onClick={submitCategory}>
              {catForm.id != null ? '保存修改' : '创建分类'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 标签弹窗 */}
      <Dialog open={tagDialogOpen} onOpenChange={(open) => !open && setTagDialogOpen(false)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{tagForm.id != null ? '编辑标签' : '新建标签'}</DialogTitle>
            <DialogDescription>标签用于商品热度与筛选。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">标签名称</Label>
              <Input
                value={tagForm.name}
                placeholder="例如：九成新"
                onChange={(e) => setTagForm({ ...tagForm, name: e.target.value })}
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">排序（越小越靠前）</Label>
              <Input
                type="number"
                value={tagForm.sort}
                onChange={(e) => setTagForm({ ...tagForm, sort: Number(e.target.value) })}
              />
            </div>
            {tagForm.id != null ? (
              <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2.5">
                <div>
                  <div className="text-sm font-medium">启用标签</div>
                  <div className="text-[11px] text-muted-foreground">禁用后前台不再展示该标签</div>
                </div>
                <Switch
                  checked={tagForm.status === CATALOG_STATUS_ENABLED}
                  onCheckedChange={(checked) => setTagForm({ ...tagForm, status: checked ? CATALOG_STATUS_ENABLED : CATALOG_STATUS_DISABLED })}
                />
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTagDialogOpen(false)}>
              取消
            </Button>
            <Button disabled={creatingTag || updatingTag} onClick={submitTag}>
              {tagForm.id != null ? '保存修改' : '创建标签'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
