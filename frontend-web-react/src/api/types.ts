/**
 * 前台（`frontend-web-react`）**唯一权威**接口类型文件 —— 后端契约的 TypeScript 投影。
 *
 * 生成方式：逐个打开 `backend/src/main/java/com/campus/market/{vo,dto,entity}` 源码逐字核对，
 * 并对照 `docs/contracts-frontend-web.md`（前台接口契约对照表）。字段名 = Jackson 序列化名，
 * 本文件覆盖的 VO/DTO 中**没有任何 `@JsonProperty` / `@JsonIgnore`**（已全目录 grep 确认），
 * 因此 JSON 名与 Java 字段名一致。
 *
 * 类型映射口径：
 * - `Long / Integer / int / long` → `number`
 * - `BigDecimal`（金额） → `number`（JSON 里是数字，非字符串）
 * - `LocalDateTime` → `string`（全局 `JacksonConfig` 统一 `yyyy-MM-dd HH:mm:ss`，见 `config/JacksonConfig.java:20-26`）
 * - `Boolean / boolean` → `boolean`；TINYINT 状态位 → `number`（枚举值用数字字面量联合）
 * - 枚举（字符串型） → 字符串字面量联合类型，值逐字取自后端常量/DB 注释，不得改大小写
 * - 请求体字段带 `?` 仅当后端**没有** `@NotBlank / @NotNull`（或该字段为查询参数且 DTO 里有默认值）；
 *   响应 VO 字段带 `| null` 仅当后端装配代码存在**明确的"不赋值"分支**（游客 / 非帖主 / 可选业务字段 / DB 允许 NULL）。
 * - `?` 表示"键可能不出现"，`| null` 表示"键出现但值为 null"。本仓 Jackson **未**配置
 *   `NON_NULL`（`JacksonConfig` 只注册了 LocalDateTime 序列化器），因此 null 字段会**原样输出为 `null`**，
 *   凡"后端可能不赋值"的字段一律写 `| null`；只有查询参数（URL query）才用 `?`。
 *
 * 本文件**只含类型声明**（`export interface` / `export type`），无 import、无运行时代码、无 `any`。
 * 末尾附一节"不确定项"注释：凡无法从源码 100% 确认的可空性/类型，均在此登记，未静默写死。
 */

/* ==========================================================================
 * 一、通用信封与分页（common/api/Result.java、common/api/PageResult.java）
 * ========================================================================== */

/**
 * 统一 REST 响应包装（backend/src/main/java/com/campus/market/common/api/Result.java:11-28）
 * — 全站唯一信封，HTTP 状态恒 200，`code === 0` 为成功。baseQuery 已统一解包 `data`，页面层通常不直接用本类型。
 */
export interface Result<T> {
  code: number
  message: string
  data: T
}

/**
 * 分页包装（backend/src/main/java/com/campus/market/common/api/PageResult.java:14-28）
 * — 字段与 MyBatis-Plus `IPage` 对齐（`total/pageNum/pageSize` 来自 `long`）。
 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 通用分页查询参数（backend/src/main/java/com/campus/market/common/api/PageResult.java 对应的请求侧约定）
 * — 各接口上限不同，见 `docs/contracts-frontend-web.md` §0.3。
 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}

/* ==========================================================================
 * 二、枚举字面量联合（值逐字取自后端常量 / DB 注释，见 docs/contracts-frontend-web.md §0.2）
 * ========================================================================== */

/** 商品状态（`sql/schema.sql:125`、`vo/GoodsCardVO.java:34-35`、`vo/GoodsDetailVO.java:38-39`） */
export type GoodsStatus = 'ON_SALE' | 'IN_TRANSACTION' | 'SOLD' | 'OFF_SALE' | 'DELETED'

/** 商品成色（`vo/GoodsCardVO.java:25-26`；JSON 里是**数字** 1~4，不是字符串） */
export type GoodsConditionLevel = 1 | 2 | 3 | 4

/** 商品列表排序（`dto/GoodsListQuery.java:17-20`；非法值后端回退 `latest`） */
export type GoodsSort = 'latest' | 'priceAsc' | 'priceDesc' | 'hot'

/** 求购帖状态（`sql/schema.sql:188`；`DELETED` 仅帖主查看自己的帖子时可能出现） */
export type WantPostStatus = 'OPEN' | 'DEALT' | 'CLOSED' | 'DELETED'

/** 求购帖排序（`dto/WantPostListQuery.java:14-15`） */
export type WantPostSort = 'latest' | 'budgetDesc'

/** 交换帖状态（`sql/schema.sql:292`） */
export type SwapPostStatus = 'OPEN' | 'DEALT' | 'CLOSED' | 'DELETED'

/** 求购应约状态（`vo/OfferVO.java:43-44`）：0 待处理 / 1 已接受 / 2 已拒绝 / 3 已撤回 */
export type OfferStatus = 0 | 1 | 2 | 3

/** 交换请求状态（`vo/SwapRequestVO.java:45-46`）：0 待处理 / 1 已同意 / 2 已拒绝 */
export type SwapRequestStatus = 0 | 1 | 2

/** 订单类型（`vo/OrderListVO.java:23-24`） */
export type OrderType = 'SALE' | 'PURCHASE' | 'SWAP'

/** 订单状态（`vo/OrderListVO.java:26-27`） */
export type OrderStatus = 'WAIT_CONFIRM' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'

/** 当前查看者角色（`vo/OrderListVO.java:46-47`，服务端按 viewer 计算） */
export type OrderViewRole = 'buyer' | 'seller'

/** 订单取消方（`vo/OrderDetailVO.java:29-30`、`sql/schema.sql:221`） */
export type OrderCancelledBy = 'BUYER' | 'SELLER' | 'TIMEOUT'

/** 私信消息类型（`vo/MessageVO.java:21-22`；`IMAGE` 为 P2 预留，`MessageSendDTO` 只接受 TEXT/GOODS_CARD） */
export type MsgType = 'TEXT' | 'GOODS_CARD' | 'IMAGE'

/** 通知类型（`vo/NotificationVO.java:18-19`；也是 `GET /notifications?type=` 的白名单） */
export type NotificationType = 'ORDER' | 'AUDIT' | 'REPORT' | 'CREDIT' | 'SYSTEM'

/** 0 未读 / 1 已读（`vo/NotificationVO.java:30-31`） */
export type ReadFlag = 0 | 1

/** 认证状态：0 未认证 / 1 已认证（`vo/LoginVO.java:26-27`、`sql/schema.sql:30`） */
export type AuthStatus = 0 | 1

/** 账号状态：0 正常 / 1 封禁（`vo/UserDetailVO.java:34-35`、`sql/schema.sql:31`） */
export type AccountStatus = 0 | 1

/** 举报对象类型（`vo/ReportVO.java:24-25`、`dto/ReportCreateDTO.java:22-23` 的 `@Pattern`） */
export type ReportTargetType = 'GOODS' | 'WANT' | 'SWAP' | 'USER'

/** 举报类型（`vo/ReportVO.java:32-33`、`dto/ReportCreateDTO.java:31-32` 的 `@Pattern`） */
export type ReportType = 'VIOLATION' | 'FRAUD' | 'COUNTERFEIT' | 'OTHER'

/** 举报工单状态：0 待处理 / 1 已处置 / 2 已驳回（`vo/ReportVO.java:40-41`） */
export type ReportStatus = 0 | 1 | 2

/** 信用变动原因（`entity/CreditLog.java:22-26`） */
export type CreditLogReason =
  | 'ORDER_COMPLETE'
  | 'REPORT_VALID'
  | 'CANCEL_TIMEOUT'
  | 'MALICIOUS_REPORT'
  | 'ADMIN_ADJUST'

/** 是否接受补差价：0 否 / 1 是（`vo/SwapPostCardVO.java:36-37`、`dto/SwapPostPublishDTO.java:38-40`） */
export type DiffFlag = 0 | 1

/* ==========================================================================
 * 三、用户与认证
 * ========================================================================== */

/**
 * LoginVO（backend/src/main/java/com/campus/market/vo/LoginVO.java:12-34）
 * — `POST /auth/login`、`POST /auth/register`、`POST /auth/refresh` 的统一响应：双 token + 用户摘要 + 封禁信息。
 */
export interface LoginVO {
  token: string
  refreshToken: string
  userId: number
  username: string
  nickname: string
  /** 头像 URL；DB 允许 NULL（`sql/schema.sql:26`，空=前端默认头像） */
  avatar: string | null
  authStatus: AuthStatus
  /** 是否处于封禁状态（`status == 1`） */
  banned: boolean
  /** 封禁理由；未封禁为 null */
  banReason: string | null
  /**
   * ⚠️ 唯一的时间格式例外：后端写的是 `user.getBannedUntil().toString()`（`AuthServiceImpl.java:229`），
   * 即 `LocalDateTime.toString()` 的 ISO 带 `T` 形式（秒为 0 时会省略 `:ss`），与 `/users/me` 的空格格式不同。
   */
  bannedUntil: string | null
}

/**
 * UserDetailVO（backend/src/main/java/com/campus/market/vo/UserDetailVO.java:14-52）
 * — `GET /users/me` 与 `GET /users/{id}` 的响应；个人主页/个人中心。注意主键字段名是 `id`（不是 `userId`）。
 */
export interface UserDetailVO {
  id: number
  nickname: string
  /** 头像 URL；DB 允许 NULL */
  avatar: string | null
  /** 学院；DB 允许 NULL */
  college: string | null
  /** 简介（入库前已 XSS 转义）；DB 允许 NULL */
  bio: string | null
  /** 信用分（DB NOT NULL DEFAULT 100） */
  creditScore: number
  /** 信用档位中文：优秀 / 良好 / 一般 / 受限（服务端按阈值计算） */
  creditLevel: string
  authStatus: AuthStatus
  /** 账号状态：0 正常 / 1 封禁（刷新 `/users/me` 后仍可展示封禁提示条） */
  status: AccountStatus
  /** 封禁理由；未封禁为 null */
  banReason: string | null
  /** 封禁截止时间（`LocalDateTime` → 空格格式）；永久封禁为 null */
  bannedUntil: string | null
  /** 在售商品数 */
  onSaleCount: number
  /** 脱敏学号；未认证为 null（`UserServiceImpl.java:54-58`） */
  studentNoMasked: string | null
  /** 脱敏校园邮箱；未认证为 null */
  campusEmailMasked: string | null
  createdAt: string
}

/* ==========================================================================
 * 四、分类与标签
 * ========================================================================== */

/**
 * CategoryTreeVO（backend/src/main/java/com/campus/market/vo/CategoryTreeVO.java:15-29）
 * — `GET /categories` 的二级分类树节点（一级节点 `children` 为启用中的二级分类）。
 */
export interface CategoryTreeVO {
  id: number
  /** 父分类 ID，0 = 一级分类（`sql/schema.sql:86` NOT NULL DEFAULT 0） */
  parentId: number
  name: string
  /** 图标名或 URL；DB 允许 NULL */
  icon: string | null
  sort: number
  /** 二级分类；`CategoryTreeVO.children` 初始化为空数组，永不 null */
  children: CategoryTreeVO[]
}

/**
 * TagVO（backend/src/main/java/com/campus/market/vo/TagVO.java:11-15）
 * — `GET /tags` 的标签项（发布表单多选 0~5 个）。
 */
export interface TagVO {
  id: number
  name: string
}

/**
 * GoodsTagVO（backend/src/main/java/com/campus/market/vo/GoodsTagVO.java:11-15）
 * — 商品**详情**里的标签对象 `[{ id, name }]`（列表侧是 `string[]`，见 `GoodsCardVO.tags`）。
 */
export interface GoodsTagVO {
  id: number
  name: string
}

/**
 * SellerVO（backend/src/main/java/com/campus/market/vo/SellerVO.java:12-26）
 * — 商品卖家 / 帖子发布者 / 订单双方的用户摘要（昵称、头像、信用分、认证状态）。
 * ⚠️ 字段是 `creditScore`（数字）而不是 `creditLevel`（字符串），后者只在 `UserDetailVO` 上。
 */
export interface SellerVO {
  id: number
  nickname: string
  /** 头像 URL；DB 允许 NULL（空=前端默认头像） */
  avatar: string | null
  /** 信用分 0~150 */
  creditScore: number
  authStatus: AuthStatus
}

/* ==========================================================================
 * 五、商品
 * ========================================================================== */

/**
 * GoodsCardVO（backend/src/main/java/com/campus/market/vo/GoodsCardVO.java:16-44）
 * — 全站复用的商品卡片：`GET /goods`、`GET /goods/my`、首页/热榜/相似推荐、收藏列表内嵌商品。
 */
export interface GoodsCardVO {
  id: number
  title: string
  /** 面交价，0 = 免费（JSON 数字） */
  price: number
  conditionLevel: GoodsConditionLevel
  /** 封面缩略图 URL（sort=0 的图片；无图时为 null，`GoodsServiceImpl.java:511`） */
  coverUrl: string | null
  /** 标签**名**列表（列表形状；详情用 `GoodsTagVO[]`） */
  tags: string[]
  /** 商品状态（列表恒为 ON_SALE；收藏列表可能为 OFF_SALE / DELETED 等） */
  status: GoodsStatus
  viewCount: number
  wantCount: number
  favoriteCount: number
  createdAt: string
}

/**
 * GoodsImageVO（backend/src/main/java/com/campus/market/vo/GoodsImageVO.java:11-20）
 * — 商品详情图集项（按 `sort` 升序）。
 */
export interface GoodsImageVO {
  url: string
  thumbUrl: string
  /** 顺序 0~8，0 为封面 */
  sort: number
}

/**
 * GoodsDetailVO（backend/src/main/java/com/campus/market/vo/GoodsDetailVO.java:17-78）
 * — `GET /goods/{id}` 的商品详情。注意它**不继承** `GoodsCardVO`，字段自成一套（无 `coverUrl`/`tags: string[]`）。
 */
export interface GoodsDetailVO {
  id: number
  title: string
  description: string
  /** 面交价，0 = 免费 */
  price: number
  conditionLevel: GoodsConditionLevel
  /** 教材课程名（教材书籍分类填写）；DB 允许 NULL */
  courseName: string | null
  /** ISBN；DB 允许 NULL */
  isbn: string | null
  /** 常约交易地点；DB 允许 NULL */
  tradeLocation: string | null
  status: GoodsStatus
  /** 二级分类 ID（`fillCategory` 命中分类时赋值） */
  categoryId: number
  /** 二级分类名 */
  categoryName: string
  /** 一级分类 ID；商品分类即一级、或父分类缺失时为 null */
  parentCategoryId: number | null
  /** 一级分类名；同上可能为 null */
  parentCategoryName: string | null
  viewCount: number
  wantCount: number
  favoriteCount: number
  heatScore: number
  createdAt: string
  updatedAt: string
  /** 图集（永不为 null，可能为空数组） */
  images: GoodsImageVO[]
  /** 标签对象数组（详情形状，与列表的 `string[]` 不同） */
  tags: GoodsTagVO[]
  /** 卖家摘要 */
  seller: SellerVO
  /** 当前登录用户是否已收藏；**游客为 null**（`GoodsServiceImpl.java:289-294`） */
  favorited: boolean | null
  /** 相似推荐位（M2 恒为空数组，M5 由推荐服务填充） */
  similarGoods: GoodsCardVO[]
}

/* ==========================================================================
 * 六、收藏
 * ========================================================================== */

/**
 * FavoriteVO（backend/src/main/java/com/campus/market/vo/FavoriteVO.java:14-26）
 * — `GET /favorites/my` 的收藏列表项。
 */
export interface FavoriteVO {
  id: number
  goodsId: number
  /**
   * 商品卡片（含最新状态，便于灰态展示已下架/已删除商品）。
   * ⚠️ 可能为 null：`FavoriteServiceImpl.java:135-139` 只在商品行存在时赋值，
   * 调用点必须先 `filter(f => f.goods)` 再渲染。
   */
  goods: GoodsCardVO | null
  createdAt: string
}

/* ==========================================================================
 * 七、求购（want-posts）
 * ========================================================================== */

/**
 * WantPostCardVO（backend/src/main/java/com/campus/market/vo/WantPostCardVO.java:16-49）
 * — `GET /want-posts` 求购广场卡片。
 */
export interface WantPostCardVO {
  id: number
  title: string
  description: string
  /** 心理价；**null = 价格面议**（不用 0 伪装免费） */
  budget: number | null
  /** 期望二级分类 ID */
  categoryId: number
  /** 分类名（`fillCard` 命中分类时赋值） */
  categoryName: string
  /** 一级分类 ID（编辑表单回填 cascader 用）；分类即一级或父分类缺失时为 null */
  parentCategoryId: number | null
  parentCategoryName: string | null
  /** 发布者摘要 */
  publisher: SellerVO
  status: WantPostStatus
  /** 待处理应约数（卡片展示口径） */
  offerCount: number
  /** 当前登录用户在该帖的应约状态：**null = 未应约** / 0 待处理 / 1 已接受 / 2 已拒绝 / 3 已撤回 */
  myOfferStatus: OfferStatus | null
  createdAt: string
}

/**
 * OfferVO（backend/src/main/java/com/campus/market/vo/OfferVO.java:12-50）
 * — 求购应约项：`WantPostDetailVO.offers[]`（仅帖主可见）与 `myOffer`。
 */
export interface OfferVO {
  id: number
  wantPostId: number
  /** 求购帖标题（"我的应约"列表展示用） */
  wantPostTitle: string
  /** 求购帖状态 */
  wantPostStatus: WantPostStatus
  /** 应约人 ID */
  userId: number
  nickname: string
  /** 头像 URL；DB 允许 NULL */
  avatar: string | null
  /** 应约人信用分 */
  creditScore: number
  authStatus: AuthStatus
  /** 报价 */
  price: number
  /** 留言；DB 允许 NULL */
  message: string | null
  status: OfferStatus
  /** 被接受后生成的订单 ID；未成单为 null */
  orderId: number | null
  createdAt: string
}

/**
 * WantPostDetailVO（backend/src/main/java/com/campus/market/vo/WantPostDetailVO.java:16-37，`extends WantPostCardVO`）
 * — `GET /want-posts/{id}`：卡片字段 + 应约列表 + 求购者操作标记。
 * 权限口径：`offers` 仅**帖主本人**返回（非帖主为 null，而非空数组），其他登录用户只看 `myOffer`。
 */
export interface WantPostDetailVO extends WantPostCardVO {
  /** 应约列表；非帖主为 null（`WantPostServiceImpl.java:253-255`） */
  offers: OfferVO[] | null
  /** 当前登录用户自己的应约；游客或未应约为 null */
  myOffer: OfferVO | null
  /** 当前登录用户是否已有待处理应约；**游客为 null**（仅登录用户赋值） */
  hasPendingOffer: boolean | null
  /** 是否可应约（已认证、未受限、非本人、帖子 OPEN） */
  canOffer: boolean
  /** 是否可编辑/关闭（仅帖主且帖子 OPEN） */
  canManage: boolean
  /** 成交后生成的订单 ID（DEALT 时回填）；未成交为 null */
  orderId: number | null
  updatedAt: string
}

/* ==========================================================================
 * 八、交换（swap-posts）
 * ========================================================================== */

/**
 * SwapPostCardVO（backend/src/main/java/com/campus/market/vo/SwapPostCardVO.java:15-55）
 * — `GET /swap-posts` 交换广场卡片。
 */
export interface SwapPostCardVO {
  id: number
  title: string
  /** 物品二级分类 ID */
  categoryId: number
  /** 分类名（`fillCard` 命中分类时赋值） */
  categoryName: string
  /** 一级分类 ID（编辑表单回填 cascader 用）；分类即一级或父分类缺失时为 null */
  parentCategoryId: number | null
  parentCategoryName: string | null
  /** 帖主物品描述 */
  myItemDesc: string
  /** 期望物品描述 */
  wantItemDesc: string
  /** 是否接受补差价：0 否 / 1 是 */
  allowDiff: DiffFlag
  /** 期望差价金额；`allowDiff=0` 时为 null */
  diffAmount: number | null
  /** 帖主摘要 */
  publisher: SellerVO
  status: SwapPostStatus
  /** 待处理请求数（"已有 N 人想换"） */
  pendingRequestCount: number
  /** 当前登录用户在该帖的请求状态：**null = 未发起** / 0 待处理 / 1 已同意 / 2 已拒绝 */
  myRequestStatus: SwapRequestStatus | null
  createdAt: string
}

/**
 * SwapRequestVO（backend/src/main/java/com/campus/market/vo/SwapRequestVO.java:16-52）
 * — 交换请求项：`SwapPostDetailVO.requests[]`（仅帖主可见）与 `myRequest`。
 */
export interface SwapRequestVO {
  id: number
  swapPostId: number
  /** 发起方 ID */
  userId: number
  nickname: string
  /** 头像 URL；DB 允许 NULL */
  avatar: string | null
  creditScore: number
  authStatus: AuthStatus
  /** 发起方物品说明 */
  itemDesc: string
  /** 关联的发起方在售商品 ID；**可空**（未关联商品时为 null） */
  goodsId: number | null
  /** 关联商品标题；未关联或商品行缺失时为 null */
  goodsTitle: string | null
  /** 关联商品封面缩略图；未关联或商品无图时为 null */
  goodsCoverUrl: string | null
  /** 关联商品价格；未关联时为 null */
  goodsPrice: number | null
  status: SwapRequestStatus
  /** 被同意后生成的订单 ID；未成单为 null */
  orderId: number | null
  createdAt: string
}

/**
 * SwapPostDetailVO（backend/src/main/java/com/campus/market/vo/SwapPostDetailVO.java:16-34，`extends SwapPostCardVO`）
 * — `GET /swap-posts/{id}`：卡片字段 + 请求列表 + 帖主操作标记。
 * 权限口径：`requests` 仅**帖主本人**返回（非帖主为 null）。
 */
export interface SwapPostDetailVO extends SwapPostCardVO {
  /** 请求列表；非帖主为 null（`SwapPostServiceImpl.java:265-268`） */
  requests: SwapRequestVO[] | null
  /** 当前登录用户自己的请求；游客或未发起为 null */
  myRequest: SwapRequestVO | null
  /** 是否可发起交换（已认证、未受限、非本人、帖子 OPEN） */
  canRequest: boolean
  /** 是否可编辑/关闭（仅帖主且帖子 OPEN） */
  canManage: boolean
  /** 成交后生成的 SWAP 订单 ID（DEALT 时回填）；未成交为 null */
  orderId: number | null
  updatedAt: string
}

/* ==========================================================================
 * 九、订单
 * ========================================================================== */

/**
 * OrderListVO（backend/src/main/java/com/campus/market/vo/OrderListVO.java:16-86）
 * — `GET /orders` 订单行卡：商品缩略 + 对方 + 金额 + 状态 + 服务端计算的 `can*` 动作标记。
 * ⚠️ 对方信息是**扁平字段**（`counterpartId / counterpartNickname / counterpartAvatar /
 * counterpartCreditScore`），后端**没有** `counterpart` 嵌套对象，也没有 `goodsCover` 字段（是 `goodsCoverUrl`）。
 */
export interface OrderListVO {
  id: number
  /** 业务单号 SH+yyyyMMdd+N 位 */
  orderNo: string
  type: OrderType
  status: OrderStatus
  /** 关联商品 ID；PURCHASE/SWAP 订单可空（`sql/schema.sql:212`） */
  goodsId: number | null
  /** 商品标题（SOLD/软删仍可展示；PURCHASE/SWAP 回溯来源帖标题，回溯不到为 null） */
  goodsTitle: string | null
  /** 商品封面缩略图 URL；无图 / 非商品订单为 null */
  goodsCoverUrl: string | null
  /** 成交金额快照（JSON 数字） */
  amount: number
  buyerId: number
  sellerId: number
  /** 当前查看者角色：buyer / seller */
  viewRole: OrderViewRole
  /** 对方用户 ID */
  counterpartId: number
  counterpartNickname: string
  /** 对方头像 URL；DB 允许 NULL */
  counterpartAvatar: string | null
  /** 对方信用分（CreditTag 展示用） */
  counterpartCreditScore: number
  /** 当前查看者是否已评价该订单（"去评价"按钮显隐） */
  hasReviewedByMe: boolean
  /** 卖家确认出售（WAIT_CONFIRM 且我是卖家） */
  canConfirm: boolean
  /** 卖家拒绝（WAIT_CONFIRM 且我是卖家） */
  canReject: boolean
  /** 买家取消（WAIT_CONFIRM/SCHEDULED 且我是买家） */
  canCancel: boolean
  /** 确认完成（SALE/PURCHASE：SCHEDULED 且我是卖家；SWAP：SCHEDULED 且我方尚未确认） */
  canComplete: boolean
  /** 去评价（COMPLETED 且 7 天窗口内且我方未评） */
  canReview: boolean
  createdAt: string
  /** 完成时间；未完成为 null */
  completedAt: string | null
  /** 取消时间；未取消为 null */
  cancelledAt: string | null
}

/**
 * ReviewVO（backend/src/main/java/com/campus/market/vo/ReviewVO.java:14-37）
 * — 交易评价项：`GET /reviews?revieweeId=` 列表与 `OrderDetailVO.reviews[]` 共用。
 */
export interface ReviewVO {
  id: number
  orderId: number
  /** 评价人 ID */
  reviewerId: number
  reviewerNickname: string
  /** 评价人头像 URL；DB 允许 NULL */
  reviewerAvatar: string | null
  /** 被评价人 ID（DB NOT NULL） */
  revieweeId: number
  /** 1~5 星 */
  score: number
  /** 评价内容；DB 允许 NULL（选填） */
  content: string | null
  createdAt: string
}

/**
 * OrderDetailVO（backend/src/main/java/com/campus/market/vo/OrderDetailVO.java:15-42，`extends OrderListVO`）
 * — `GET /orders/{id}`：行卡字段 + 时间线 + SWAP 双确认 + 取消原因 + 双方互评。
 */
export interface OrderDetailVO extends OrderListVO {
  /** 卖家确认时间（SWAP 创建即 SCHEDULED，= 创建时间）；未确认为 null */
  confirmedAt: string | null
  /** 买方完成确认时间（SWAP 双确认展示用）；未确认为 null */
  buyerConfirmedAt: string | null
  /** 卖方完成确认时间；未确认为 null */
  sellerConfirmedAt: string | null
  /** 取消方；未取消为 null */
  cancelledBy: OrderCancelledBy | null
  /** 取消理由；无则 null */
  cancelReason: string | null
  /** 卖方摘要（`toSellerVO` 在用户行缺失时返回 null，见文末不确定项） */
  seller: SellerVO | null
  /** 买方摘要（SWAP 对等参与者展示） */
  buyer: SellerVO | null
  /** 本订单全部评价（通常 0~2 条，永不为 null） */
  reviews: ReviewVO[]
}

/* ==========================================================================
 * 十、信用流水（GET /credits/my 直接返回实体）
 * ========================================================================== */

/**
 * CreditLog（backend/src/main/java/com/campus/market/entity/CreditLog.java:19-64）
 * — `GET /credits/my` **直接返回实体**（无 VO），时间字段走全局 `yyyy-MM-dd HH:mm:ss`（无 `@JsonFormat` 注解）。
 */
export interface CreditLog {
  id: number
  /** 被变动用户 ID */
  userId: number
  /** 变动值，正加负减（DB NOT NULL） */
  scoreChange: number
  /** 变动前分值 */
  beforeScore: number
  /** 变动后分值 */
  afterScore: number
  reason: CreditLogReason
  /** 多态关联类型：ORDER / REPORT；DB 允许 NULL */
  refType: string | null
  /** 关联单据 ID；DB 允许 NULL */
  refId: number | null
  /** 备注（ADMIN_ADJUST 必填）；DB 允许 NULL */
  remark: string | null
  /** 操作管理员 ID（仅 ADMIN_ADJUST）；DB 允许 NULL */
  operatorId: number | null
  createdAt: string
  updatedAt: string
}

/* ==========================================================================
 * 十一、聊天（REST + WebSocket 共用）
 * ========================================================================== */

/**
 * ConversationVO（backend/src/main/java/com/campus/market/vo/ConversationVO.java:14-33）
 * — `GET /chats/conversations` 会话列表项（按 `lastMsgAt` 倒序分页）。
 */
export interface ConversationVO {
  id: number
  /** 对方用户 ID（聊天窗顶栏与发消息目标） */
  peerUserId: number
  /** 对方昵称（用户行缺失时后端回填"已注销用户"，见 `ChatServiceImpl.java:93`） */
  peerNickname: string
  /** 对方头像 URL；DB 允许 NULL */
  peerAvatar: string | null
  /** 最近消息摘要（派生字段）；从未有消息时为 null */
  lastMsg: string | null
  /** 最近消息时间（派生字段）；从未有消息时为 null */
  lastMsgAt: string | null
  /** 当前用户在该会话的未读数（派生字段，DB NOT NULL DEFAULT 0） */
  unreadCount: number
  updatedAt: string
}

/**
 * GoodsBriefVO（backend/src/main/java/com/campus/market/vo/GoodsBriefVO.java:14-23）
 * — GOODS_CARD 消息内嵌的商品快照（缩略图 + 标题 + 价格）。
 */
export interface GoodsBriefVO {
  goodsId: number
  title: string
  price: number
  /** 封面缩略图 URL；商品无图或商品行缺失时为 null */
  coverUrl: string | null
}

/**
 * MessageVO（backend/src/main/java/com/campus/market/vo/MessageVO.java:13-33）
 * — `GET /chats/conversations/{id}/messages`（**后端倒序返回，前端需 reverse**）、
 * `POST /chats/messages` 返回值、WebSocket `CHAT_NEW` 帧内的 `message` 皆为本类型。
 */
export interface MessageVO {
  id: number
  conversationId: number
  senderId: number
  msgType: MsgType
  /** TEXT = 文本 / GOODS_CARD = 商品 ID 字符串（DB NOT NULL） */
  content: string
  /** 接收方阅读时间；null = 未读（发送方据此展示"已读/未读"） */
  readAt: string | null
  createdAt: string
  /** msgType = GOODS_CARD 时的商品快照，其余为 null */
  goods: GoodsBriefVO | null
}

/**
 * ChatUnreadVO（backend/src/main/java/com/campus/market/vo/ChatUnreadVO.java:12-18）
 * — `GET /chats/unread` 未读聚合（导航红点数据源）。字段是原始 `long`，恒为数字。
 */
export interface ChatUnreadVO {
  /** 有未读消息的会话数 */
  conversationCount: number
  /** 未读消息总数（红点徽标展示值） */
  messageCount: number
}

/* ==========================================================================
 * 十二、站内通知
 * ========================================================================== */

/**
 * NotificationVO（backend/src/main/java/com/campus/market/vo/NotificationVO.java:14-33）
 * — `GET /notifications` 通知列表项；跳转由前端按 `refType/refId` 处理。
 */
export interface NotificationVO {
  id: number
  type: NotificationType
  title: string
  content: string
  /** 跳转类型（ORDER / GOODS / REPORT 等）；DB 允许 NULL */
  refType: string | null
  /** 跳转对象 ID；DB 允许 NULL */
  refId: number | null
  isRead: ReadFlag
  createdAt: string
}

/* ==========================================================================
 * 十三、举报
 * ========================================================================== */

/**
 * ReportVO（backend/src/main/java/com/campus/market/vo/ReportVO.java:17-52）
 * — `GET /reports/my` 举报工单列表项（后台 ADM-04 复用同一 VO）。
 * ⚠️ `images` 是**字符串数组**（与商品图片的对象数组不同形状）；`targetTitle` 为目标标题快照，目标彻底不存在时为 null。
 */
export interface ReportVO {
  id: number
  /**
   * 举报人 ID。
   * ⚠️ 前台链路恒为 null：`ReportServiceImpl.toVO`（:284-299）**从未 setReporterId**，
   * 只有后台 `AdminReportServiceImpl` 会赋值。前台不要依赖该字段。
   */
  reporterId: number | null
  targetType: ReportTargetType
  targetId: number
  /** 目标标题（GOODS/WANT/SWAP→title，USER→nickname）；目标彻底不存在时为 null */
  targetTitle: string | null
  reportType: ReportType
  /** 补充描述；DB 允许 NULL */
  description: string | null
  /** 举报截图 URL 列表；反序列化失败或为空时返回**空数组**（`ReportServiceImpl.java:327-338`），永不 null */
  images: string[]
  status: ReportStatus
  /** 状态中文：待处理 / 已处置 / 已驳回（status 为 null 时后端返回空串） */
  statusText: string
  /** 处置说明（后台写入）；未处置为 null */
  result: string | null
  /** 处置时间；未处置为 null */
  handledAt: string | null
  createdAt: string
}

/* ==========================================================================
 * 十四、上传
 * ========================================================================== */

/**
 * UploadVO（backend/src/main/java/com/campus/market/vo/UploadVO.java:11-17）
 * — `POST /uploads`（form-data 字段名固定 `file`）的响应；webp 不生成缩略图时 `thumbUrl` 回原图 URL。
 */
export interface UploadVO {
  /** 原图 URL：/upload/{yyyyMM}/{uuid}.{ext} */
  url: string
  /** 缩略图 URL */
  thumbUrl: string
}

/* ==========================================================================
 * 十五、请求 DTO（请求体 / 查询参数）
 * ========================================================================== */

/**
 * LoginDTO（backend/src/main/java/com/campus/market/dto/LoginDTO.java:12-18）
 * — `POST /auth/login` 请求体。
 * 说明：`AuthController` **没有**内部类 `LoginBody`；登录/刷新体分别是 `dto/LoginDTO.java` 与 `dto/RefreshDTO.java`。
 */
export interface LoginDTO {
  username: string
  password: string
}

/**
 * RegisterDTO（backend/src/main/java/com/campus/market/dto/RegisterDTO.java:13-25）
 * — `POST /auth/register` 请求体（注册即自动登录，响应同 LoginVO）。
 * ⚠️ 前端旧版多传 `confirmPassword`（后端无此字段，被 Jackson 静默忽略）；本类型**不**声明该字段。
 */
export interface RegisterDTO {
  /** 3~50 字符 */
  username: string
  /** 6~64 字符 */
  password: string
  /** ≤50 字符 */
  nickname: string
}

/**
 * RefreshDTO（backend/src/main/java/com/campus/market/dto/RefreshDTO.java:12-15）
 * — `POST /auth/refresh` 请求体（refreshToken 换新双 token；**前台旧版未接线**，保持不接）。
 */
export interface RefreshDTO {
  refreshToken: string
}

/**
 * SendVerifyCodeDTO（backend/src/main/java/com/campus/market/dto/SendVerifyCodeDTO.java:14-23）
 * — `POST /auth/verify-code/send` 请求体（需登录）。
 */
export interface SendVerifyCodeDTO {
  /** ≤30 字符 */
  studentNo: string
  /** 校园邮箱，≤100 字符，`@Email` 校验 */
  campusEmail: string
}

/**
 * VerifyCheckDTO（backend/src/main/java/com/campus/market/dto/VerifyCheckDTO.java:14-26）
 * — `POST /auth/verify-code/check` 请求体（6 位数字验证码，需登录）。
 */
export interface VerifyCheckDTO {
  /** ≤30 字符 */
  studentNo: string
  /** ≤100 字符 */
  campusEmail: string
  /** 6 位数字（`@Pattern("\\d{6}")`） */
  code: string
}

/**
 * UserEditDTO（backend/src/main/java/com/campus/market/dto/UserEditDTO.java:13-26）
 * — `PUT /users/me` 请求体（bio 入库前 XSS 转义）。注意**只提交这 4 个字段**。
 */
export interface UserEditDTO {
  /** ≤50 字符（必填） */
  nickname: string
  /** ≤255 字符，可选 */
  avatar?: string | null
  /** ≤100 字符，可选 */
  college?: string | null
  /** ≤200 字符，可选 */
  bio?: string | null
}

/**
 * FavoriteAddDTO（backend/src/main/java/com/campus/market/dto/FavoriteAddDTO.java:12-16）
 * — `POST /favorites` 请求体（重复收藏幂等）。
 */
export interface FavoriteAddDTO {
  goodsId: number
}

/**
 * GoodsImageDTO（backend/src/main/java/com/campus/market/dto/GoodsImageDTO.java:13-21）
 * — 商品发布/编辑时的图片项（数组顺序即 sort，服务端按下标写入 0~8）。
 */
export interface GoodsImageDTO {
  /** 原图 URL（必填，`@NotBlank`） */
  url: string
  /** 缩略图 URL（必填，`@NotBlank`；webp 与原图同 URL） */
  thumbUrl: string
}

/**
 * GoodsPublishDTO（backend/src/main/java/com/campus/market/dto/GoodsPublishDTO.java:18-58）
 * — `POST /goods` / `PUT /goods/{id}` 请求体（发布与编辑复用同一结构）。
 */
export interface GoodsPublishDTO {
  /** ≤50 字（必填） */
  title: string
  /** ≤500 字（必填） */
  description: string
  /** 二级分类 ID（必填，parent_id != 0） */
  categoryId: number
  /** 成色 1~4（必填） */
  conditionLevel: GoodsConditionLevel
  /** 面交价 ≥0，0 = 免费（必填） */
  price: number
  /** 常约交易地点 ≤100 字，选填 */
  tradeLocation?: string | null
  /** 教材课程名 ≤100 字，选填 */
  courseName?: string | null
  /** ISBN ≤20 字符，选填 */
  isbn?: string | null
  /** 标签 ID 集合，0~5 个，选填 */
  tagIds?: number[]
  /**
   * 图片列表 1~9 张。
   * ⚠️ 该字段**没有** `@NotNull` 注解，但 `GoodsServiceImpl.rebuildImagesAndTags`（:365-368）要求 1~9 张，
   * 缺失/超限会返回 40002/40003，因此类型按业务必填声明。
   */
  images: GoodsImageDTO[]
}

/**
 * GoodsListQuery（backend/src/main/java/com/campus/market/dto/GoodsListQuery.java:14-50）
 * — `GET /goods` 查询参数（小驼峰绑定；分页默认 1/20，无 `@Max` 上限）。
 */
export interface GoodsListQuery {
  /** 页码，从 1 开始（DTO 默认 1，可省） */
  pageNum?: number
  /** 每页条数（DTO 默认 20；本接口无 `@Max`） */
  pageSize?: number
  /** 分类 ID（一级自动展开其二级子类；二级精确匹配） */
  categoryId?: number
  /** 成色 1~4 */
  conditionLevel?: GoodsConditionLevel
  /** 最低价（含） */
  minPrice?: number
  /** 最高价（含） */
  maxPrice?: number
  /** 关键词（ngram 全文检索 title+description） */
  q?: string
  /** 教材课程名（模糊匹配） */
  courseName?: string
  /** ISBN（模糊匹配） */
  isbn?: string
  /** 排序方式，非法值后端回退 latest */
  sort?: GoodsSort
}

/**
 * WantPostPublishDTO（backend/src/main/java/com/campus/market/dto/WantPostPublishDTO.java:17-34）
 * — `POST /want-posts` / `PUT /want-posts/{id}` 请求体。
 */
export interface WantPostPublishDTO {
  /** ≤50 字（必填） */
  title: string
  /** ≤500 字（必填） */
  description: string
  /** 期望二级分类 ID（必填） */
  categoryId: number
  /** 心理价 ≥0；**省略或 null = 价格面议**（后端不用 0 表示面议） */
  budget?: number | null
}

/**
 * WantPostListQuery（backend/src/main/java/com/campus/market/dto/WantPostListQuery.java:12-36）
 * — `GET /want-posts` 查询参数（默认只返回 OPEN，DELETED 永不返回）。
 */
export interface WantPostListQuery {
  /** 页码，从 1 开始（默认 1） */
  pageNum?: number
  /** 每页条数（默认 20，上限 100） */
  pageSize?: number
  /** 状态筛选：OPEN（DTO 默认）/ DEALT / CLOSED；**DELETED 不接受** */
  status?: Exclude<WantPostStatus, 'DELETED'>
  /** 期望分类 ID（一级自动展开二级子类） */
  categoryId?: number
  /** 只看我发布的（需登录） */
  mine?: boolean
  /** 关键词（模糊匹配标题+描述） */
  q?: string
  /** 排序：latest（默认）/ budgetDesc */
  sort?: WantPostSort
}

/**
 * OfferCreateDTO（backend/src/main/java/com/campus/market/dto/OfferCreateDTO.java:15-23）
 * — `POST /want-posts/{postId}/offers` 请求体（报价 + 留言）。
 */
export interface OfferCreateDTO {
  /** 报价 ≥0（必填） */
  price: number
  /** 留言 ≤200 字，选填（敏感词校验） */
  message?: string | null
}

/**
 * SwapPostPublishDTO（backend/src/main/java/com/campus/market/dto/SwapPostPublishDTO.java:17-43）
 * — `POST /swap-posts` / `PUT /swap-posts/{id}` 请求体。
 */
export interface SwapPostPublishDTO {
  /** ≤50 字（必填） */
  title: string
  /** 物品二级分类 ID（必填） */
  categoryId: number
  /** 我的物品描述 ≤500 字（必填） */
  myItemDesc: string
  /** 想要的物品描述 ≤500 字（必填） */
  wantItemDesc: string
  /** 是否接受补差价：0 否 / 1 是（必填） */
  allowDiff: DiffFlag
  /** 期望差价金额：`allowDiff=1` 时必填且 ≥0；`allowDiff=0` 时服务端置 NULL（DTO 无注解） */
  diffAmount?: number | null
}

/**
 * SwapRequestCreateDTO（backend/src/main/java/com/campus/market/dto/SwapRequestCreateDTO.java:13-21）
 * — `POST /swap-posts/{postId}/requests` 请求体（发起交换）。
 */
export interface SwapRequestCreateDTO {
  /** 发起方物品说明 ≤500 字（必填，敏感词校验） */
  itemDesc: string
  /** 关联的自己在售商品 ID，可选（须为本人 ON_SALE 商品） */
  goodsId?: number | null
}

/**
 * SwapPostListQuery（backend/src/main/java/com/campus/market/dto/SwapPostListQuery.java:11-29）
 * — `GET /swap-posts` 查询参数（默认只返回 OPEN，DELETED 永不返回）。
 */
export interface SwapPostListQuery {
  /** 页码，从 1 开始（默认 1） */
  pageNum?: number
  /** 每页条数（默认 20，上限 100） */
  pageSize?: number
  /** 状态筛选：OPEN（DTO 默认）/ DEALT / CLOSED；**DELETED 不接受** */
  status?: Exclude<SwapPostStatus, 'DELETED'>
  /** 物品分类 ID（一级自动展开二级子类） */
  categoryId?: number
  /** 只看我发布的 */
  mine?: boolean
  /** 关键词（模糊匹配标题+物品描述） */
  q?: string
}

/**
 * OrderCreateDTO（backend/src/main/java/com/campus/market/dto/OrderCreateDTO.java:12-16）
 * — `POST /orders` 请求体（买家对在售商品创建 SALE 订单，响应为新订单 ID）。
 */
export interface OrderCreateDTO {
  goodsId: number
}

/**
 * OrderActionDTO（backend/src/main/java/com/campus/market/dto/OrderActionDTO.java:12-16）
 * — `POST /orders/{id}/cancel` 与 `POST /orders/{id}/reject` 请求体（理由可选，≤200 字）。
 */
export interface OrderActionDTO {
  /** 取消/拒绝理由（可选） */
  reason?: string | null
}

/**
 * ActionReasonDTO（backend/src/main/java/com/campus/market/dto/ActionReasonDTO.java:13-17）
 * — `POST /want-posts/{id}/close`、`POST /want-posts/offers/{offerId}/reject`、
 * `POST /swap-posts/{id}/close`、`POST /swap-posts/requests/{requestId}/reject` 的通用请求体。
 */
export interface ActionReasonDTO {
  /** 理由 ≤200 字（可选） */
  reason?: string | null
}

/**
 * ReviewCreateDTO（backend/src/main/java/com/campus/market/dto/ReviewCreateDTO.java:15-25）
 * — `POST /orders/{id}/review` 请求体（1~5 星 + 可选内容）。
 */
export interface ReviewCreateDTO {
  /** 评分 1~5（必填） */
  score: number
  /** 评价内容 ≤200 字，选填（敏感词校验） */
  content?: string | null
}

/**
 * MessageSendDTO（backend/src/main/java/com/campus/market/dto/MessageSendDTO.java:16-29）
 * — `POST /chats/messages` 请求体（返回值是 MessageVO，可直接入列）。
 */
export interface MessageSendDTO {
  /** 会话对方用户 ID（必填） */
  peerUserId: number
  /** 消息类型：TEXT / GOODS_CARD（IMAGE 为 P2 预留，本期不开放） */
  msgType: MsgType
  /** TEXT = 文本（≤500 字）/ GOODS_CARD = 商品 ID 字符串 */
  content: string
}

/**
 * ReportCreateDTO（backend/src/main/java/com/campus/market/dto/ReportCreateDTO.java:19-41）
 * — `POST /reports` 请求体（白名单在 DTO 层 `@Pattern` 拦截）。
 */
export interface ReportCreateDTO {
  targetType: ReportTargetType
  targetId: number
  reportType: ReportType
  /** 补充描述 ≤500 字，选填 */
  description?: string | null
  /** 举报截图 URL 列表，最多 3 张，选填（**字符串数组**，非对象数组） */
  images?: string[]
}

/* ==========================================================================
 * 十六、前端组合类型（非后端直出，供 store / 页面使用）
 * ========================================================================== */

/**
 * UserInfo — 登录态用户摘要（前端组合类型，无对应后端 VO）。
 * 来源口径：`frontend-web/src/stores/user.js` 的 `applyAuth`（:18-33，来自 LoginVO）
 * 与 `fetchProfile`（:46-60，来自 UserDetailVO）两次写入的**合并结果**，
 * React 版对应 `frontend-web-react/src/features/auth/authSlice.ts` 的 `setCredentials` / `setProfile`。
 * - `userId` 双来源：LoginVO 是 `userId`，UserDetailVO 主键是 `id` → 由 slice 显式映射；
 * - `banned` 双来源：LoginVO 的 `banned`（boolean）与 UserDetailVO 的 `status === 1`；
 * - `banReason` / `bannedUntil` 在 store 内已被 `|| ''` 兜底为空串，因此这里是 `string`（不是 `| null`）。
 */
export interface UserInfo {
  userId: number
  nickname: string
  /** 可与 LoginVO.avatar / UserDetailVO.avatar 的 null 对齐 */
  avatar: string | null
  authStatus: number
  /** 仅 `/users/me` 拉取后存在；登录即时写入时为 undefined */
  creditScore?: number | null
  banned: boolean
  /** store 内 `|| ''` 兜底，恒为字符串 */
  banReason: string
  /** store 内 `|| ''` 兜底，恒为字符串；登录来源为 ISO 带 T，资料来源为空格格式 */
  bannedUntil: string
  college?: string | null
  bio?: string | null
  studentNoMasked?: string | null
  campusEmailMasked?: string | null
  createdAt?: string
}

/**
 * GoodsTagLike — 商品标签**双形状**兼容取值类型（前端组合类型，无对应后端 VO）。
 * 列表侧（`GoodsCardVO.tags`）是 `string[]`，详情侧（`GoodsDetailVO.tags`）是 `GoodsTagVO[]`，
 * 旧版 Vue 有兼容垫片（`frontend-web/src/components/GoodsCard.vue:51`），React 版按需复用同一取值函数。
 */
export type GoodsTagLike = string | GoodsTagVO

/* ==========================================================================
 * 附：无法从源码 100% 确认的可空性 / 类型项（未静默写死，需实测或用例覆盖）
 * --------------------------------------------------------------------------
 * 1) 联查用户字段（`GoodsDetailVO.seller`、`WantPostCardVO.publisher`、`SwapPostCardVO.publisher`、
 *    `OfferVO.nickname/avatar/creditScore/authStatus`、`SwapRequestVO` 同名四项、`ReviewVO.reviewerNickname/
 *    reviewerAvatar`、`OrderListVO.counterpartNickname/counterpartCreditScore`）：
 *    装配代码只在 `map.get(id) != null` 时赋值，理论上可为 null；本文件按 `FK ... ON DELETE RESTRICT`
 *    （用户行不可物理删除）判为**非空**。若日后放开用户物理删除，需全部改为 `| null`。
 * 2) `GoodsDetailVO.categoryId/categoryName`、`WantPostCardVO.categoryName`、`SwapPostCardVO.categoryName`：
 *    `fillCategory` / `fillCard` 在分类行缺失时**整体跳过赋值** → 理论可 null；本文件按非空处理
 *    （`category_id` NOT NULL + FK 保证）。`parentCategoryId/parentCategoryName` 则**确实可空**（一级分类 /
 *    父分类缺失），已标 `| null`。
 * 3) `ReportVO.reporterId`：前台链路 `ReportServiceImpl.toVO`（:284-299）从未赋值，恒为 null；
 *    已标 `number | null`。若产品需要"我举报的举报人"，需后端补字段。
 * 4) `LoginVO.bannedUntil`：`LocalDateTime.toString()` 在秒为 0 时**省略 `:ss`**（如 `2026-10-16T12:30`），
 *    因此不能假设固定长度；前端只应"非空即展示"，或交给宽容解析。
 * 5) `MessageVO.msgType` 是否会出现 `'IMAGE'`：DB 注释允许 TEXT/GOODS_CARD/IMAGE，
 *    当前服务端只产出 TEXT / GOODS_CARD；本文件把 `'IMAGE'` 纳入联合以防未知取值，需实测确认。
 * 6) 请求侧 `pageNum/pageSize`：`GoodsListQuery` / `WantPostListQuery` / `SwapPostListQuery` 在 DTO 里有
 *    默认值（1 / 20），故标 `?`；而 `/goods/my`（默认 10）、`/credits/my`（默认 10、`@Max(50)`）等接口
 *    用的是 `@RequestParam(defaultValue=...)` 而非 DTO，其上限/默认值见 `docs/contracts-frontend-web.md` §0.3。
 * 7) 数值型枚举：`conditionLevel`、`authStatus`、`status`（用户/举报）、`isRead`、`allowDiff`、
 *    应约/交换请求的 `status` 在 JSON 里是**数字**；对照表 §0.2 用 `1|2|3|4` 书写，勿当字符串处理。
 * 8) `GoodsDetailVO.favorited` 只在登录时赋值：游客得到 `null`（已标 `boolean | null`），
 *    页面判"未收藏"应写 `favorited === true` 而非真值判断。
 * 9) `WantPostDetailVO.offers` / `SwapPostDetailVO.requests` 非帖主时为 `null`（**不是空数组**），
 *    判断"有无应约"要先 `?? []`。
 * 10) `OrderDetailVO.seller/buyer`：`OrderServiceImpl.toSellerVO`（:612-615）显式 `return null`，
 *     故确实可空（已标 `SellerVO | null`）。
 * 11) `CreditLog.createdAt/updatedAt`：实体类**无** `@JsonFormat`，实际格式依赖全局 `JacksonConfig`
 *     （`yyyy-MM-dd HH:mm:ss`），已按全局口径写 `string`。
 * 12) 类型化枚举带来的调用点约束：`lib/constants.ts` 中 `REPORT_TYPE_OPTIONS` 等选项常量是 `string`，
 *     赋给本文件的联合类型字段（如 `ReportCreateDTO.reportType`）需先收窄或断言；
 *     查询参数 `sort` / `status` 亦为联合类型，从 URL query（`string`）取值时需校验后再传入。
 * 13) `GoodsPublishDTO.images`：DTO 无 `@NotNull`，但服务层强制 1~9 张；本文件按业务必填声明。
 *     若上游确实可能传空数组，改为 `images?: GoodsImageDTO[]` 并在提交前校验。
 * 14) `WantPostDetailVO.status` / `SwapPostDetailVO.status` 含 `'DELETED'`：帖主查看自己已软删的帖子时
 *     后端直接返回实体状态，列表接口则永不返回 DELETED。
 * ========================================================================== */
