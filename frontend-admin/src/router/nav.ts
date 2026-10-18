import {
  LayoutDashboard,
  Newspaper,
  Flag,
  Users,
  FolderTree,
  Settings2,
  Lock,
  ScrollText,
  ShieldCheck,
  type LucideIcon
} from 'lucide-react'
import type { AdminRole } from '@/api/types'
import { ALL_ROLES } from '@/lib/constants'

export interface NavItem {
  path: string
  title: string
  icon: LucideIcon
  /** 可见角色（与后端 @RequireRole 一致） */
  roles: AdminRole[]
  desc: string
}

/** 菜单与 PRD §4.2 RBAC 权限矩阵一致；路由守卫已拦截，这里只控制可见性 */
export const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', title: '数据大屏', icon: LayoutDashboard, roles: ALL_ROLES, desc: '平台核心指标与趋势' },
  { path: '/content', title: '内容管理', icon: Newspaper, roles: ['super', 'auditor'], desc: '商品/求购/交换巡查处置' },
  { path: '/report', title: '举报工作台', icon: Flag, roles: ['super', 'auditor'], desc: '工单核查与组合处置' },
  { path: '/user', title: '用户管理', icon: Users, roles: ['super', 'auditor'], desc: '封禁、解封与信用调整' },
  { path: '/category', title: '分类管理', icon: FolderTree, roles: ['super', 'operator'], desc: '分类树与标签维护' },
  { path: '/operation', title: '运营配置', icon: Settings2, roles: ['super', 'operator'], desc: '轮播图与平台公告' },
  { path: '/sensitive-word', title: '敏感词库', icon: Lock, roles: ['super', 'auditor'], desc: '内容安全词库与导入' },
  { path: '/log', title: '操作日志', icon: ScrollText, roles: ALL_ROLES, desc: '审计日志（只读）' },
  { path: '/admin', title: '管理员管理', icon: ShieldCheck, roles: ['super'], desc: '后台账号与权限' }
]

export const navItemByPath = (path: string): NavItem | undefined =>
  NAV_ITEMS.find((item) => item.path === path)
