import { useMemo, useState } from 'react'
import { Plus, RotateCcw, Search, Trash2, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { useConfirm } from '@/components/confirm'
import { PageHeader } from '@/components/PageHeader'
import { Pagination } from '@/components/Pagination'
import { TableSkeleton } from '@/components/TableSkeleton'
import { EmptyState } from '@/components/EmptyState'
import { DateTimeText } from '@/components/DateTimeText'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
  useAddSensitiveWordMutation,
  useDeleteSensitiveWordMutation,
  useGetSensitiveWordsQuery,
  useImportSensitiveWordsMutation
} from '@/api/catalogApi'
import type { PageResult, SensitiveWord } from '@/api/types'

/** 兼容两种返回形态：PageResult<SensitiveWord> 或平铺数组（旧接口） */
function normalize(data: PageResult<SensitiveWord> | SensitiveWord[] | undefined, keyword: string, pageNum: number, pageSize: number) {
  if (Array.isArray(data)) {
    const filtered = keyword ? data.filter((w) => (w.word || '').includes(keyword)) : data
    return {
      list: filtered.slice((pageNum - 1) * pageSize, pageNum * pageSize),
      total: filtered.length
    }
  }
  return { list: data?.list ?? [], total: data?.total ?? 0 }
}

export default function SensitiveWordPage() {
  const confirm = useConfirm()

  const [wordInput, setWordInput] = useState('')
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)

  const [importOpen, setImportOpen] = useState(false)
  const [importText, setImportText] = useState('')

  const { data, isFetching } = useGetSensitiveWordsQuery({ keyword: keyword || undefined, pageNum, pageSize })
  const [addWord, { isLoading: adding }] = useAddSensitiveWordMutation()
  const [deleteWord] = useDeleteSensitiveWordMutation()
  const [importWords, { isLoading: importing }] = useImportSensitiveWordsMutation()

  const { list: rows, total } = useMemo(() => normalize(data, keyword, pageNum, pageSize), [data, keyword, pageNum, pageSize])

  const onAdd = async () => {
    const word = wordInput.trim()
    if (!word) {
      toast.warning('请输入敏感词')
      return
    }
    try {
      await addWord(word).unwrap()
      toast.success('已添加')
      setWordInput('')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onDelete = async (row: SensitiveWord) => {
    const ok = await confirm({
      title: '删除敏感词',
      description: `确认删除「${row.word}」？删除后不再参与内容校验。`,
      confirmText: '确认删除',
      tone: 'destructive'
    })
    if (!ok) return
    try {
      await deleteWord(row.id).unwrap()
      toast.success('已删除')
    } catch {
      /* baseQuery 已提示 */
    }
  }

  const onImport = async () => {
    const words = importText
      .split(/[\n,，、;；\s]+/)
      .map((w) => w.trim())
      .filter(Boolean)
    const unique = Array.from(new Set(words))
    if (!unique.length) {
      toast.warning('未解析到有效的敏感词')
      return
    }
    try {
      const added = await importWords(unique).unwrap()
      // 后端返回「实际新增」条数（已按库内去重 + 归一化），不要用前端提交数冒报
      const count = typeof added === 'number' ? added : unique.length
      toast.success(
        count === unique.length
          ? `已导入 ${count} 个敏感词`
          : `已导入 ${count} 个敏感词（提交 ${unique.length} 个，其余已存在）`
      )
      setImportOpen(false)
      setImportText('')
      setPageNum(1)
    } catch {
      /* baseQuery 已提示 */
    }
  }

  return (
    <div>
      <PageHeader
        title="敏感词库"
        description="内容安全词库维护与批量导入（ADM-03，超级管理员 / 内容审核员）"
        actions={
          <Button variant="outline" onClick={() => setImportOpen(true)}>
            <Upload className="size-4" />
            批量导入
          </Button>
        }
      />

      <Card className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">新增敏感词</Label>
            <Input
              value={wordInput}
              placeholder="输入敏感词后回车或点击添加"
              className="w-[240px]"
              onChange={(e) => setWordInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') onAdd()
              }}
            />
          </div>
          <Button onClick={onAdd} disabled={adding}>
            <Plus className="size-4" />
            添加
          </Button>

          <div className="ml-auto flex items-end gap-3">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">关键词</Label>
              <Input
                value={keywordInput}
                placeholder="按敏感词筛选"
                className="w-[200px]"
                onChange={(e) => setKeywordInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setKeyword(keywordInput.trim())
                    setPageNum(1)
                  }
                }}
              />
            </div>
            <Button
              variant="outline"
              onClick={() => {
                setKeyword(keywordInput.trim())
                setPageNum(1)
              }}
            >
              <Search className="size-4" />
              查询
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setKeywordInput('')
                setKeyword('')
                setPageNum(1)
              }}
            >
              <RotateCcw className="size-4" />
              重置
            </Button>
          </div>
        </div>
      </Card>

      <Card className="mt-4 p-4">
        {isFetching && !rows.length ? (
          <TableSkeleton />
        ) : rows.length ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[80px]">ID</TableHead>
                <TableHead>敏感词</TableHead>
                <TableHead className="w-[180px]">添加时间</TableHead>
                <TableHead className="w-[120px] text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.id}>
                  <TableCell className="tabular-nums text-muted-foreground">#{row.id}</TableCell>
                  <TableCell>
                    <span className="stamp-badge">{row.word}</span>
                  </TableCell>
                  <TableCell>
                    <DateTimeText value={row.createdAt} className="text-muted-foreground" />
                  </TableCell>
                  <TableCell>
                    <div className="flex justify-end">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => onDelete(row)}
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
          <EmptyState title="暂无敏感词" description="可在上方输入添加，或使用批量导入" />
        )}

        {total > 0 ? (
          <Pagination
            pageNum={pageNum}
            pageSize={pageSize}
            total={total}
            onPageChange={(next, size) => {
              setPageNum(next)
              setPageSize(size)
            }}
          />
        ) : null}
      </Card>

      <Dialog open={importOpen} onOpenChange={(open) => !open && setImportOpen(false)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>批量导入敏感词</DialogTitle>
            <DialogDescription>
              支持换行、逗号、空格、分号分隔；导入时会自动去重。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">敏感词列表</Label>
            <Textarea
              rows={8}
              value={importText}
              placeholder={'代写论文\n刷单\n外挂'}
              onChange={(e) => setImportText(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setImportOpen(false)}>
              取消
            </Button>
            <Button disabled={importing} onClick={onImport}>
              开始导入
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
