// 后端契约类型（严格对齐 backend 的 VO / 实体出参，字段名与序列化口径保持一致）

/** 统一 REST 响应包装：{ code, message, data }，code=0 成功 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/** 分页包装（与 MyBatis-Plus PageResult 对齐） */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 分页查询参数 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

export type AdminRole = 'super' | 'auditor' | 'operator'

/** POST /auth/login 返回（AdminLoginVO，扁平结构） */
export interface AdminLoginVO {
  token: string
  refreshToken?: string
  adminId: number
  username: string
  realName?: string
  role: AdminRole
  mustChangePassword?: boolean
}

/** GET /auth/me（AdminVO） */
export interface AdminVO {
  id: number
  username: string
  realName?: string
  role: AdminRole
  status?: number
  mustChangePassword?: number
  lastLoginAt?: string
  createdAt?: string
}

/** 前端登录态中的管理员信息（统一扁平口径） */
export interface AdminInfo {
  id: number
  username: string
  realName?: string
  role: AdminRole
  mustChangePassword?: boolean
}

/** 用户管理 VO（AdminUserVO，不含 password） */
export interface AdminUserVO {
  id: number
  username: string
  nickname?: string
  avatar?: string
  college?: string
  bio?: string
  creditScore?: number
  authStatus?: number
  status?: number
  banReason?: string
  bannedUntil?: string
  lastLoginAt?: string
  createdAt?: string
}

/** 信用流水（CreditLog） */
export interface CreditLog {
  id: number
  userId?: number
  scoreChange: number
  beforeScore?: number
  afterScore?: number
  reason?: string
  refType?: string
  refId?: number
  remark?: string
  createdAt?: string
}

/** 内容巡查 VO（AdminContentVO，三类内容统一投影） */
export interface AdminContentVO {
  targetType: 'GOODS' | 'WANT' | 'SWAP'
  id: number
  title?: string
  ownerId?: number
  status?: string
  amount?: number | string | null
  offSaleReason?: string
  deletedAt?: string
  deleteReason?: string
  createdAt?: string
}

/** 举报工单 VO（ReportVO） */
export interface ReportVO {
  id: number
  reporterId?: number
  targetType?: 'GOODS' | 'WANT' | 'SWAP' | 'USER'
  targetId?: number
  targetTitle?: string
  reportType?: string
  description?: string
  images?: string[] | string | null
  status?: number
  statusText?: string
  result?: string
  handledAt?: string
  createdAt?: string
}

/** 分类实体（Category） */
export interface Category {
  id: number
  parentId?: number
  name: string
  icon?: string
  sort?: number
  status?: number
  createdAt?: string
  updatedAt?: string
}

/** 标签实体（Tag） */
export interface Tag {
  id: number
  name: string
  sort?: number
  status?: number
  createdAt?: string
  updatedAt?: string
}

/** 敏感词实体（SensitiveWord） */
export interface SensitiveWord {
  id: number
  word: string
  createdAt?: string
}

/** 轮播实体（Banner） */
export interface Banner {
  id: number
  title?: string
  imageUrl?: string
  linkUrl?: string
  sort?: number
  status?: number
}

/** 公告实体（Notice） */
export interface Notice {
  id: number
  title?: string
  content?: string
  status?: number
  publisherId?: number
  publishedAt?: string
  createdAt?: string
}

/** 操作日志实体（OperationLog） */
export interface OperationLog {
  id: number
  adminId?: number
  adminUsername?: string
  action?: string
  targetType?: string
  targetId?: number
  detail?: string
  ip?: string
  createdAt?: string
}

/** DAU 单点 */
export interface DauPoint {
  date: string
  activeUsers: number
}

/** 数据大屏聚合 VO（DashboardVO） */
export interface DashboardVO {
  userTotal: number
  userTodayNew: number
  goodsOnSale: number
  orderTotal: number
  gmvCompleted: number
  orderWaitConfirm: number
  orderScheduled: number
  reportPending: number
  creditDistribution?: Record<string, number>
  ordersWeekTrend?: number[]
  authUserTotal: number
  reportAvgHandleHours: number
  dauTrend30?: DauPoint[]
}

/** 图片上传返回（UploadVO） */
export interface UploadVO {
  url: string
  thumbUrl: string
}
