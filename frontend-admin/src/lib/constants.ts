import type { AdminRole } from '@/api/types'

/** 三角色（PRD §4.2 RBAC 权限矩阵，口径小写） */
export const ALL_ROLES: AdminRole[] = ['super', 'auditor', 'operator']

export const ROLE_META: Record<AdminRole, { label: string; badge: string; desc: string }> = {
  super: { label: '超级管理员', badge: 'destructive', desc: '全部权限，含管理员账号与信用调整' },
  auditor: { label: '内容审核员', badge: 'warning', desc: '内容巡查、举报处置、敏感词库' },
  operator: { label: '运营员', badge: 'info', desc: '分类标签、轮播公告等运营配置' }
}

export const roleLabel = (role?: string) => ROLE_META[role as AdminRole]?.label || '未登录'

/** 内容巡查状态映射（与后端状态取值一致） */
type StatusMeta = { label: string; badge: string }
export const CONTENT_STATUS_MAPS: Record<string, Record<string, StatusMeta>> = {
  GOODS: {
    ON_SALE: { label: '在售', badge: 'success' },
    OFF_SALE: { label: '已下架', badge: 'warning' },
    IN_TRANSACTION: { label: '交易中', badge: 'info' },
    SOLD: { label: '已售出', badge: 'soft' },
    DELETED: { label: '已删除', badge: 'destructive' }
  },
  WANT: {
    OPEN: { label: '开放中', badge: 'success' },
    DEALT: { label: '已成交', badge: 'soft' },
    CLOSED: { label: '已关闭', badge: 'warning' },
    DELETED: { label: '已删除', badge: 'destructive' }
  },
  SWAP: {
    OPEN: { label: '开放中', badge: 'success' },
    DEALT: { label: '已成交', badge: 'soft' },
    CLOSED: { label: '已关闭', badge: 'warning' },
    DELETED: { label: '已删除', badge: 'destructive' }
  }
}

export const contentStatusMeta = (targetType: string, status?: string): StatusMeta =>
  CONTENT_STATUS_MAPS[targetType]?.[status || ''] || { label: status || '未知', badge: 'soft' }

/** 举报对象类型 */
export const REPORT_TARGET_MAPS: Record<string, StatusMeta> = {
  GOODS: { label: '商品', badge: 'success' },
  WANT: { label: '求购', badge: 'info' },
  SWAP: { label: '交换', badge: 'warning' },
  USER: { label: '用户', badge: 'destructive' }
}

/** 举报类型 */
export const REPORT_TYPE_MAPS: Record<string, StatusMeta> = {
  VIOLATION: { label: '违规', badge: 'warning' },
  FRAUD: { label: '欺诈', badge: 'destructive' },
  COUNTERFEIT: { label: '假货', badge: 'destructive' },
  OTHER: { label: '其他', badge: 'soft' }
}

/** 举报工单状态：0 待处理 / 1 已处置 / 2 已驳回 */
export const REPORT_STATUS_MAPS: Record<number, StatusMeta> = {
  0: { label: '待处理', badge: 'warning' },
  1: { label: '已处置', badge: 'success' },
  2: { label: '已驳回', badge: 'soft' }
}

/** 举报处置动作（可组合） */
export const REPORT_ACTIONS: { value: string; label: string }[] = [
  { value: 'TAKE_DOWN', label: '下架内容' },
  { value: 'WARN', label: '警告' },
  { value: 'DEDUCT', label: '扣信用分' },
  { value: 'BAN', label: '封禁账号' }
]

/** 公告状态：0 草稿 / 1 已发布 / 2 已下线 */
export const NOTICE_STATUS_MAPS: Record<number, StatusMeta> = {
  0: { label: '草稿', badge: 'soft' },
  1: { label: '已发布', badge: 'success' },
  2: { label: '已下线', badge: 'warning' }
}

/** 信用流水原因（与后端 CreditLog.REASON_* 对齐） */
export const CREDIT_REASON_LABELS: Record<string, string> = {
  ORDER_COMPLETE: '完成交易',
  REPORT_VALID: '举报属实',
  CANCEL_TIMEOUT: '超时未确认',
  MALICIOUS_REPORT: '恶意举报',
  ADMIN_ADJUST: '管理员调整'
}

/** 操作日志已知动作（下拉建议，可自由输入） */
export const KNOWN_ACTIONS = [
  'ADMIN_RESET_PASSWORD',
  'ADMIN_CREATE',
  'ADMIN_UPDATE',
  'ADMIN_DISABLE',
  'ADMIN_ENABLE',
  'GOODS_TAKE_DOWN',
  'GOODS_RESTORE',
  'GOODS_DELETE',
  'USER_BAN',
  'USER_UNBAN',
  'CREDIT_ADJUST',
  'WORD_ADD',
  'WORD_DELETE',
  'WORD_IMPORT',
  'REPORT_HANDLE',
  'CATEGORY_CREATE',
  'CATEGORY_UPDATE',
  'CATEGORY_DELETE',
  'TAG_CREATE',
  'TAG_UPDATE',
  'TAG_DELETE',
  'BANNER_CREATE',
  'BANNER_UPDATE',
  'BANNER_DELETE',
  'NOTICE_CREATE',
  'NOTICE_UPDATE',
  'NOTICE_PUBLISH',
  'NOTICE_OFFLINE',
  'NOTICE_DELETE'
]

/** 操作日志动作着色 */
export function actionBadge(action?: string): string {
  if (!action) return 'soft'
  if (/DELETE|BAN|DISABLE|TAKE_DOWN|OFFLINE/.test(action)) return 'destructive'
  if (/RESTORE|UNBAN|ENABLE|PUBLISH|CREATE/.test(action)) return 'success'
  return 'info'
}

/** 信用分档位配色：<60 危险 / <75 警告 / <90 主色 / ≥90 优秀 */
export function creditTone(score?: number): 'danger' | 'warning' | 'primary' | 'success' | 'muted' {
  const n = Number(score)
  if (!Number.isFinite(n)) return 'muted'
  if (n < 60) return 'danger'
  if (n < 75) return 'warning'
  if (n < 90) return 'primary'
  return 'success'
}
