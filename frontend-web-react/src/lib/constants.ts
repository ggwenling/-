/**
 * 前台共享常量（枚举、文案映射、分页尺寸、错误码）。
 *
 * 原则：**只放已经从 Vue 版源码逐字核对过的值**。凡是文案尚未核对的，一律先不写死，
 * 在对应页面实现时读取 `frontend-web/src/views/**` 原文补齐（避免自造产品文案）。
 *
 * 数据来源：`frontend-web/src/views/**`、`src/api/request.js`、`src/utils/auth.js`、
 * `docs/contracts-frontend-web.md`（契约对照表）。
 */

/** 统一响应成功码 */
export const SUCCESS_CODE = 0
/** 登录态失效码：清登录态并跳登录页 */
export const AUTH_EXPIRED_CODES = [40100, 40101]
/** 账号被封禁（弹窗告知，不登出） */
export const CODE_BANNED = 40301
/** 信用分受限（<60） */
export const CODE_RESTRICTED = 40302

/** 未读红点轮询间隔（Vue 版 stores/msg.js 硬编码 30s） */
export const POLL_INTERVAL_MS = 30000

/** 商品成色（GoodsCard / GoodsListView / GoodsDetailView 三处重复定义，此处统一） */
export const CONDITION_TEXT: Record<number, string> = {
  1: '全新',
  2: '几乎全新',
  3: '轻微使用',
  4: '明显使用'
}

/** 商品状态文案 */
export const GOODS_STATUS_TEXT: Record<string, string> = {
  ON_SALE: '在售',
  IN_TRANSACTION: '交易中',
  SOLD: '已售出',
  OFF_SALE: '已下架',
  DELETED: '已删除'
}

/** 求购帖状态文案 */
export const WANT_STATUS_TEXT: Record<string, string> = {
  OPEN: '求购中',
  DEALT: '已成交',
  CLOSED: '已关闭'
}

/** 交换帖状态文案 */
export const SWAP_STATUS_TEXT: Record<string, string> = {
  OPEN: '交换中',
  DEALT: '已成交',
  CLOSED: '已关闭'
}

/** 应约状态（求购） */
export const OFFER_STATUS_TEXT: Record<number, string> = {
  0: '待处理',
  1: '已接受',
  2: '已拒绝',
  3: '已撤回'
}

/** 交换请求状态 */
export const SWAP_REQUEST_STATUS_TEXT: Record<number, string> = {
  0: '待处理',
  1: '已同意',
  2: '已拒绝'
}

/** 信用流水原因（与后端 CreditLog.reason 对齐，Vue 版 ProfileView 逐字） */
export const CREDIT_REASON_TEXT: Record<string, string> = {
  ORDER_COMPLETE: '完成交易加分',
  REPORT_VALID: '发布违规内容',
  CANCEL_TIMEOUT: '订单超时未确认',
  MALICIOUS_REPORT: '恶意举报',
  ADMIN_ADJUST: '管理员调整'
}

/** 举报工单状态 */
export const REPORT_STATUS_TEXT: Record<number, string> = {
  0: '待处理',
  1: '已处置',
  2: '已驳回'
}

export const REPORT_STATUS_TONE: Record<number, 'warning' | 'success' | 'info'> = {
  0: 'warning',
  1: 'success',
  2: 'info'
}

/** 举报类型（个人中心展示口径） */
export const REPORT_TYPE_TEXT: Record<string, string> = {
  VIOLATION: '违规内容',
  FRAUD: '诈骗欺诈',
  COUNTERFEIT: '假冒伪劣',
  OTHER: '其他'
}

/** 举报对象类型 */
export const REPORT_TARGET_TEXT: Record<string, string> = {
  GOODS: '商品',
  WANT: '求购',
  SWAP: '交换',
  USER: '用户'
}

/** 举报弹窗可选项（ReportDialog.vue 的 REPORT_TYPES 数组） */
export const REPORT_TYPE_OPTIONS: { value: string; label: string }[] = [
  { value: 'VIOLATION', label: '违规内容' },
  { value: 'FRAUD', label: '诈骗欺诈' },
  { value: 'COUNTERFEIT', label: '假冒伪劣' },
  { value: 'OTHER', label: '其他' }
]

/** 订单取消方文案 */
export const CANCELLED_BY_TEXT: Record<string, string> = {
  BUYER: '买家取消',
  SELLER: '卖家拒绝',
  TIMEOUT: '超时自动取消'
}

/** 订单类型文案 */
export const ORDER_TYPE_TEXT: Record<string, string> = {
  SALE: '出售',
  PURCHASE: '求购',
  SWAP: '交换'
}

/** 消息类型 */
export const MSG_TYPE = {
  TEXT: 'TEXT',
  GOODS_CARD: 'GOODS_CARD'
} as const

/** 通知类型图标键（与 Vue 版 MessageCenterView 的映射键一致，图标在组件内映射） */
export const NOTIFICATION_TYPES = ['ORDER', 'AUDIT', 'REPORT', 'CREDIT', 'SYSTEM'] as const

/** 信用分档位阈值（PRD §5.7） */
export const CREDIT_MAX = 150
export const CREDIT_EXCELLENT = 120
export const CREDIT_GOOD = 80
export const CREDIT_RESTRICTED = 60

/** 信用档位 */
export function creditLevel(score?: number | null): { text: string; tone: 'success' | 'default' | 'warning' | 'destructive' | 'soft' } {
  const n = Number(score)
  if (!Number.isFinite(n)) return { text: '—', tone: 'soft' }
  if (n >= CREDIT_EXCELLENT) return { text: '优秀', tone: 'success' }
  if (n >= CREDIT_GOOD) return { text: '良好', tone: 'default' }
  if (n >= CREDIT_RESTRICTED) return { text: '一般', tone: 'warning' }
  return { text: '受限', tone: 'destructive' }
}

/** 教材专区：一级分类 id 硬编码为 1（Vue 版 GoodsPublishView 同口径，与后端种子数据耦合） */
export const TEXTBOOK_CATEGORY_ID = 1

/** 各页分页尺寸（Vue 版硬编码值，逐页保留） */
export const PAGE_SIZE = {
  homeLatest: 12,
  goodsList: 20,
  square: 10,
  conversations: 100,
  messages: 20,
  notifications: 20,
  orders: 20,
  orderPageSizes: [20, 40, 60],
  profileGoods: 50,
  profileFavorites: 50,
  profileOrders: 10,
  profileReviews: 20,
  profileCredits: 10,
  profileReports: 20
} as const

/** 商品可发布图片数量（后端 1~9） */
export const GOODS_IMAGE_MIN = 1
export const GOODS_IMAGE_MAX = 9
/** 举报证据图上限（后端 ≤3） */
export const REPORT_IMAGE_MAX = 3
/** 标签上限 */
export const GOODS_TAG_MAX = 5
/** 上传单文件上限（后端 5MB） */
export const UPLOAD_MAX_BYTES = 5 * 1024 * 1024
export const UPLOAD_ACCEPT = 'image/jpeg,image/png,image/webp'
