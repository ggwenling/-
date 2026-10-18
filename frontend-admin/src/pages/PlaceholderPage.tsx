import { Construction } from 'lucide-react'
import { EmptyState } from '@/components/EmptyState'

/** 施工中占位页（各模块完成后替换） */
export default function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="paper-card px-6 py-10">
      <EmptyState
        icon={<Construction className="size-6" />}
        title={`${title} · 建设中`}
        description="该模块正在迁移到新前端，稍后交付。"
      />
    </div>
  )
}
