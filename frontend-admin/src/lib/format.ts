/**
 * 后端 LocalDateTime 由全局 JacksonConfig 序列化为「yyyy-MM-dd HH:mm:ss」
 * （联调实测：2026-10-16 12:25:31，非 ISO）。此处统一转换为：
 * - datetime：yyyy-MM-dd HH:mm
 * - date：yyyy-MM-dd
 * 同时兼容 ISO 形态（replace('T',' ')），与旧版 frontend-admin 的 DateTimeText 口径一致。
 */
export function formatDateTime(value?: string | null, mode: 'datetime' | 'date' = 'datetime'): string {
  const raw = value || ''
  if (!raw) return '—'
  const text = String(raw).replace('T', ' ')
  return mode === 'date' ? text.slice(0, 10) : text.slice(0, 16)
}

/** 金额格式化：¥1,234.00；空值返回占位符 */
export function formatAmount(value?: number | string | null, placeholder = '—'): string {
  if (value === null || value === undefined || value === '') return placeholder
  const n = Number(value)
  if (!Number.isFinite(n)) return placeholder
  return `¥${n.toFixed(2)}`
}

/** 安全取数：非有限数字回退 0（大屏字段防御式取值） */
export function num(value: unknown): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}
