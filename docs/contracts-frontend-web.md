# 前台接口契约对照表（`frontend-web-react` ⇄ `backend`）

> 用途：`frontend-web`（Vue3 + Element Plus）重构为 React 版时的**唯一契约真源**。新应用 `frontend-web-react` 只允许按本表调用接口、按本表字段取值；任何与本表不符的写法都视为缺陷。
>
> 核对方式：`frontend-web/src/api/*.js` 的 66 个导出函数逐条对照 `backend/src/main/java/com/campus/market/` 的 controller / dto / vo / 全局配置源码（文件:行号为证据）。**结论：无一个后端缺失；2 处需注意（`register` 多传字段、`pageReviews` 的 `revieweeId` 可能为空）；1 个后端端点前台未接线（`POST /api/auth/refresh`）。**
>
> 生成时间：2026-10-16 · 生成会话：DSH（DeepSeek）· 依据：只读源码比对（未运行、未修改任何文件）

---

## 0. 全局口径（写代码前必须先读）

| 项 | 契约 | 证据 |
| --- | --- | --- |
| baseURL | `/api`（所有 controller 前缀 `/api`；**上传是 `/api/uploads`**） | `frontend-web/src/api/request.js:28-31` |
| 超时 | 15000ms | 同上 |
| 鉴权头 | `Authorization: Bearer <accessToken>` | `request.js:33-39`；`security/AuthInterceptor.java:40,119-130` |
| 响应信封 | `{ code, message, data }`，`code === 0` 为成功（**HTTP 状态恒 200**） | `common/api/Result.java:11-28`；`common/api/ErrorCode.java:12`；`common/exception/GlobalExceptionHandler.java:18-72`（全仓唯一 `@RestControllerAdvice`，无 `@ResponseStatus`/`ResponseEntity`） |
| 分页 | `{ list, total, pageNum, pageSize }` | `common/api/PageResult.java:14-28` |
| 参数缺失/类型错 | 落到兜底 handler → **`50000` 而非 `40000`**（如 `GET /orders` 缺 `role`、`GET /reviews` 缺 `revieweeId`） | `GlobalExceptionHandler.java:68-72` |
| 时间格式 | 全局 `yyyy-MM-dd HH:mm:ss`（GMT+8） | `config/JacksonConfig.java:17-25`；`resources/application.yml:10-11`；`docs/命名规范.md:30` |
| ⚠️ 时间格式例外 | **`bannedUntil` 有两种格式**：登录响应是 `String`（ISO 带 `T`），`/users/me` 是 `LocalDateTime`（空格格式） | `vo/LoginVO.java:34` + `service/impl/AuthServiceImpl.java:229` vs `vo/UserDetailVO.java:41` |
| token 键 | `campus_market_token`（access，120min）/ `campus_market_refresh_token`（refresh，7d） | `frontend-web/src/utils/auth.js:2-3`；`resources/application.yml:42-44` |
| token 存储语义 | 默认 localStorage；`remember === false` 写 sessionStorage；**写入目标时清除另一侧的同一 key**；读取时 **localStorage 优先** | `utils/auth.js:5-7,10,13-16,28-31` |
| refresh 流程 | **后端有、前台完全未接线**（`POST /api/auth/refresh`），access 过期 2h 即掉线重登 | `AuthController.java:48-52`；前端 `api/user.js` 无封装 |

### 0.1 三种"图片/标签"形状（极易混淆）

| 场景 | 形状 | 证据 |
| --- | --- | --- |
| 举报证据截图 | **字符串数组** `string[]`（前后台复用同一 `ReportVO`） | `vo/ReportVO.java:38`；`dto/ReportCreateDTO.java:41` |
| 商品图片 | **对象数组** `[{ url, thumbUrl, sort }]`（`url`/`thumbUrl` 均 `@NotBlank`） | `vo/GoodsDetailVO.java:66`；`vo/GoodsImageVO.java:13-20`；`dto/GoodsImageDTO.java:17,20` |
| 商品标签 | **双形状**：列表 `string[]`，详情 `[{ id, name }]` | `vo/GoodsCardVO.java:32` vs `vo/GoodsDetailVO.java:69`；前端兼容垫片 `components/GoodsCard.vue:51` |

### 0.2 枚举字面量（一律照抄，不得改大小写）

- 订单：`type` = `SALE | PURCHASE | SWAP`；`status` = `WAIT_CONFIRM | SCHEDULED | COMPLETED | CANCELLED`；`cancelledBy` = `BUYER | SELLER | TIMEOUT`
- 商品：`status` = `ON_SALE | IN_TRANSACTION | SOLD | OFF_SALE | DELETED`；`conditionLevel` = `1|2|3|4`
- 求购/交换帖：`status` = `OPEN | DEALT | CLOSED`
- 应约：`0 待处理 | 1 已接受 | 2 已拒绝 | 3 已撤回`；交换请求：`0 待处理 | 1 已同意 | 2 已拒绝`
- 举报：`status` = `0|1|2`；`targetType` = `GOODS | WANT | SWAP | USER`；`reportType` = `VIOLATION | FRAUD | COUNTERFEIT | OTHER`
- 通知：`type` = `ORDER | AUDIT | REPORT | CREDIT | SYSTEM`；`isRead` = `0|1`
- 信用流水 `reason` = `ORDER_COMPLETE | REPORT_VALID | CANCEL_TIMEOUT | MALICIOUS_REPORT | ADMIN_ADJUST`
- 消息：`msgType` = `TEXT | GOODS_CARD`

### 0.3 分页上限（各接口不同，写死前先查）

| 接口 | 上限 | 前端实传 |
| --- | --- | --- |
| `/goods`、`/goods/my` | 无 `@Max` | 20 / **50** |
| `/orders`、`/chats/*`、`/notifications`、`/favorites/my`、`/reports/my`、`/reviews` | `@Max(100)` | 20 / 10 / **100（触顶）** / 50 / 20 / 20 |
| `/credits/my` | `@Max(50)` | 10 |

---

## 1. 逐端点对照表

> `结论` 列：✅ 前端调用与后端一致；⚠️ 需注意；❓ 后端不存在（本表为空）。
> 「响应取值」列写的是**前端实际用到的字段**（不是后端 VO 全字段）。

### 1.1 `api/user.js`（9 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `login` | `POST /auth/login` | `{username,password}` | `data` = LoginVO：`token/refreshToken/userId/username/nickname/avatar/authStatus/banned/banReason/bannedUntil` | `AuthController.java:43-46`；`vo/LoginVO.java:12-34`；`AuthServiceImpl.java:213-231` | ✅ |
| `register` | `POST /auth/register` | `{username,password,nickname}`，**前端多传 `confirmPassword`** | 同 LoginVO（注册即登录） | `AuthController.java:37-40`；`dto/RegisterDTO.java` | ⚠️ 多字段被忽略（依赖 Spring Boot 默认关闭 `FAIL_ON_UNKNOWN_PROPERTIES`，本仓未显式配置） |
| `logout` | `POST /auth/logout` | — | — | `AuthController.java:54-59`（无 `@RequireAuth`） | ✅ |
| `getMyProfile` | `GET /users/me` | — | UserDetailVO：`id`（→ 映射为 `userId`）、`nickname/avatar/college/bio/creditScore/authStatus/status/banReason/bannedUntil/createdAt/studentNoMasked/campusEmailMasked` | `UserController.java:37-41`；`vo/UserDetailVO.java:16-52` | ✅ |
| `getMyCredits` | `GET /credits/my` | `pageNum,pageSize(@Max50)` | `list[].scoreChange/beforeScore/afterScore/reason/createdAt` | `CreditController.java:36-49`（**直接返回实体** `CreditLog`） | ✅ |
| `getUserProfile` | `GET /users/{userId}` | — | `nickname/avatar/creditLevel` | `UserController.java:59-63` | ✅ |
| `updateProfile` | `PUT /users/me` | `{nickname,avatar,college,bio}` | — | `UserController.java:50-57`；`dto/UserEditDTO.java` | ✅ |
| `sendVerifyCode` | `POST /auth/verify-code/send` | `{studentNo,campusEmail}` | — | `AuthController.java:61-68`；`dto/SendVerifyCodeDTO.java` | ✅（需登录） |
| `submitVerifyCode` | `POST /auth/verify-code/check` | `{studentNo,campusEmail,code}` | — | `AuthController.java:70-77`；`dto/VerifyCheckDTO.java` | ✅（需登录） |

### 1.2 `api/goods.js`（15 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `pageGoods` | `GET /goods` | `pageNum=1/pageSize=20/categoryId/conditionLevel/minPrice/maxPrice/q/courseName/isbn/sort(latest默认)` | `data.list/total` | `GoodsController.java:81-84`；`dto/GoodsListQuery.java:23-50` | ✅ |
| `pageMyGoods` | `GET /goods/my` | `pageNum/pageSize`（**其余 query 被静默忽略**） | `data.list` | `GoodsController.java:87-92` | ⚠️ 不要指望它支持 `status/categoryId` 过滤 |
| `getGoods` | `GET /goods/{id}` | — | GoodsDetailVO：`title/description/price/conditionLevel/tradeLocation/courseName/isbn/status/images[{url,thumbUrl}]/tags[{id,name}]/categoryId/parentCategoryId/seller{id,nickname,avatar,creditLevel,authStatus}/favorited/favoriteCount/viewCount/wantCount/createdAt/similarGoods[]` | `GoodsController.java:95-100`；`vo/GoodsDetailVO.java:17-78` | ✅ |
| `publishGoods` | `POST /goods` | `{title≤50,description≤500,categoryId,conditionLevel,price≥0,tradeLocation≤100,courseName≤100,isbn≤20,tagIds≤5,images1~9}` | `data` = 新 id | `GoodsController.java:41-44` | ✅ |
| `updateGoods` | `PUT /goods/{id}` | 同上 | — | `GoodsController.java:47-50` | ✅ |
| `deleteGoods` | `DELETE /goods/{id}` | — | — | `GoodsController.java:53-57` | ✅ |
| `offSaleGoods` | `POST /goods/{id}/off-sale` | — | — | `GoodsController.java:60-64` | ✅ |
| `onSaleGoods` | `POST /goods/{id}/on-sale` | — | — | `GoodsController.java:67-71` | ✅ |
| `restoreGoods` | `POST /goods/{id}/restore` | — | —（后端恢复到 `OFF_SALE`，需再点上架） | `GoodsController.java:74-78` | ✅ |
| `listCategories` | `GET /categories` | — | `data` = 二级树 `[{id,parentId,name,icon,sort,children[]}]` | `CategoryController.java:32-50`；`vo/CategoryTreeVO.java` | ✅ |
| `listTags` | `GET /tags` | — | `[{id,name}]` | `TagController.java:27-30` | ✅ |
| `addFavorite` | `POST /favorites` | `{goodsId}` | — | `FavoriteController.java:42-46` | ✅ |
| `removeFavorite` | `DELETE /favorites/{goodsId}` | — | — | `FavoriteController.java:49-53` | ✅ |
| `pageMyFavorites` | `GET /favorites/my` | `pageNum/pageSize(@Max100)` | `list[].goods`（**需 `filter(f => f.goods)`**） | `FavoriteController.java:56-62`；`vo/FavoriteVO.java` | ✅ |
| `uploadImage` | `POST /uploads` | FormData 字段名 **`file`** | `data` = `{url,thumbUrl}` | `UploadController.java:33-37`；`vo/UploadVO.java` | ✅ |

### 1.3 `api/order.js`（9 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `createOrder` | `POST /orders` | `{goodsId}` | **返回整个 `OrderDetailVO`**（新单 id 取 `data.id`，不是裸 id） | `OrderController.java:51-57`（`Result<OrderDetailVO>`） | ✅ 联调实测修正 |
| `pageOrders` | `GET /orders` | **`role` 必填**(`buyer/seller`)/`status?`/`pageNum/pageSize(@Max100)` | `list/total`；行：`id/orderNo/type/status/amount/goodsId/goodsTitle/goodsCoverUrl/viewRole/counterpartId/counterpartNickname/counterpartAvatar/counterpartCreditScore/hasReviewedByMe/createdAt/completedAt/cancelledAt/canConfirm/canReject/canCancel/canComplete/canReview` | `OrderController.java:59-68`；`vo/OrderListVO.java` | ⚠️ 缺 `role` → **50000**；字段名以运行时为准（`counterpartCreditScore` 而非 `counterpart.creditLevel`） |
| `getOrder` | `GET /orders/{id}` | — | OrderDetailVO：上述 + `confirmedAt/buyerConfirmedAt/sellerConfirmedAt/completedAt/cancelledAt/cancelledBy/cancelReason/reviews[{reviewerId,score,content,createdAt}]/buyer{...}/seller{...}` | `OrderController.java:70-74`；`vo/OrderDetailVO.java` | ✅ |
| `confirmOrder` | `POST /orders/{id}/confirm` | — | — | `OrderController.java:76-80` | ✅ |
| `rejectOrder` | `POST /orders/{id}/reject` | `{reason≤200}` | — | `OrderController.java:82-86` | ✅ |
| `cancelOrder` | `POST /orders/{id}/cancel` | `{reason}` | — | `OrderController.java:88-92` | ✅ |
| `completeOrder` | `POST /orders/{id}/complete` | — | — | `OrderController.java:94-98` | ✅ |
| `reviewOrder` | `POST /orders/{id}/review` | `{score 1~5,content≤200}` | — | `OrderController.java:100-104`；`dto/ReviewCreateDTO.java` | ✅ |
| `pageReviews` | `GET /reviews` | **`revieweeId` 必填**/`pageNum/pageSize(@Max100)` | `list` | `ReviewController.java:32-41`；`vo/ReviewVO.java` | ⚠️ `ProfileView` 依赖 `userInfo.userId` 回填，为空 → **50000** |

### 1.4 `api/message.js`（9 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `pageConversations` | `GET /chats/conversations` | `pageNum/pageSize(@Max100)`（前端传 **100**） | `list[].id/peerUserId/peerNickname/peerAvatar/lastMsg/lastMsgAt/unreadCount` | `ChatController.java:43-50`；`vo/ConversationVO.java` | ✅ |
| `pageChatMessages` | `GET /chats/conversations/{id}/messages` | `pageNum/pageSize` | `total/list`（**后端倒序**，前端需 `reverse()`）；`id/conversationId/senderId/msgType/content/readAt/createdAt/goods{goodsId,title,price,coverUrl}` | `ChatController.java:58-66`；`vo/MessageVO.java` | ✅ |
| `sendChatMessage` | `POST /chats/messages` | `{peerUserId,msgType,content}` | `data` = MessageVO（可直接入列） | `ChatController.java:52-56`；`ChatServiceImpl.java:114-181` | ✅ |
| `markConversationRead` | `POST /chats/conversations/{id}/read` | — | — | `ChatController.java:68-73` | ✅ |
| `getChatUnread` | `GET /chats/unread` | — | `data.messageCount`（另有 `conversationCount`） | `ChatController.java:75-79`；`vo/ChatUnreadVO.java` | ✅ |
| `pageNotifications` | `GET /notifications` | `type?`(白名单)/`pageNum/pageSize(@Max100)` | `list/total`；`id/type/title/content/refType/refId/isRead/createdAt` | `NotificationController.java:52-77`（白名单 `:46-48`） | ✅ |
| `getNotificationUnreadCount` | `GET /notifications/unread-count` | — | `data` = **裸数字** | `NotificationController.java:79-83` | ✅（与 `getChatUnread` 形状不同） |
| `markNotificationRead` | `POST /notifications/{id}/read` | — | — | `NotificationController.java:85-101` | ✅ |
| `markAllNotificationsRead` | `POST /notifications/read-all` | — | — | `NotificationController.java:103-111` | ✅ |

### 1.5 `api/want.js`（10 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `pageWantPosts` | `GET /want-posts` | `pageNum/pageSize/status(默认OPEN)/categoryId/mine/q/sort(latest\|budgetDesc)` | `list/total`；卡：`id/title/budget/status/categoryId/publisher{...}/myOfferStatus/createdAt` | `WantPostController.java:82-86`；`vo/WantPostCardVO.java` | ✅ |
| `getWantPost` | `GET /want-posts/{id}` | — | WantPostDetailVO：`title/description/budget/categoryId/parentCategoryId/publisher{...}/status/canOffer/myOffer{id,price,message,status,orderId}/offers[]` | `WantPostController.java:88-92`；`vo/WantPostDetailVO.java` | ✅ |
| `publishWantPost` | `POST /want-posts` | `{title≤50,description≤500,categoryId,budget?}` | `data` = 新 id | `WantPostController.java:48-52` | ✅（`budget` 空 = 面议） |
| `updateWantPost` | `PUT /want-posts/{id}` | 同上 | — | `WantPostController.java:54-58` | ✅ |
| `closeWantPost` | `POST /want-posts/{id}/close` | `{reason?}` | — | `WantPostController.java:60-66` | ✅ |
| `removeWantPost` | `DELETE /want-posts/{id}` | — | — | `WantPostController.java:68-73` | ✅ |
| `createOffer` | `POST /want-posts/{postId}/offers` | `{price≥0,message≤200?}` | — | `WantPostController.java:94-98`；`dto/OfferCreateDTO.java:19-23` | ✅ |
| `withdrawOffer` | `POST /want-posts/offers/{offerId}/withdraw` | — | — | `WantPostController.java:109-114` | ✅ |
| `acceptOffer` | `POST /want-posts/offers/{offerId}/accept` | — | — | `WantPostController.java:116-120` | ✅ |
| `rejectOffer` | `POST /want-posts/offers/{offerId}/reject` | `{reason?}` | — | `WantPostController.java:122-128` | ✅ |

### 1.6 `api/swap.js`（9 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `pageSwapPosts` | `GET /swap-posts` | `pageNum/pageSize/status(默认OPEN)/categoryId/mine/q` | `list/total`；卡：`id/title/myItemDesc/wantItemDesc/allowDiff/diffAmount/status/publisher{...}/myRequestStatus/createdAt` | `SwapPostController.java:81-85`；`vo/SwapPostCardVO.java` | ✅ |
| `getSwapPost` | `GET /swap-posts/{id}` | — | SwapPostDetailVO：上述 + `categoryId/parentCategoryId/canRequest/myRequest{...}/requests[]` | `SwapPostController.java:87-91`；`vo/SwapPostDetailVO.java` | ✅ |
| `publishSwapPost` | `POST /swap-posts` | `{title≤50,categoryId,myItemDesc≤500,wantItemDesc≤500,allowDiff(0/1),diffAmount(allowDiff=1 必填)}` | `data` = 新 id | `SwapPostController.java:47-51` | ✅ |
| `updateSwapPost` | `PUT /swap-posts/{id}` | 同上 | — | `SwapPostController.java:53-57` | ✅ |
| `closeSwapPost` | `POST /swap-posts/{id}/close` | `{reason?}` | — | `SwapPostController.java:59-65` | ✅ |
| `removeSwapPost` | `DELETE /swap-posts/{id}` | — | — | `SwapPostController.java:67-72` | ✅ |
| `createSwapRequest` | `POST /swap-posts/{postId}/requests` | `{itemDesc≤500,goodsId?}` | — | `SwapPostController.java:93-98` | ✅ |
| `acceptSwapRequest` | `POST /swap-posts/requests/{requestId}/accept` | — | — | `SwapPostController.java:109-113` | ✅ |
| `rejectSwapRequest` | `POST /swap-posts/requests/{requestId}/reject` | `{reason?}` | — | `SwapPostController.java:115-121` | ✅ |

### 1.7 `api/report.js`（2 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `submitReport` | `POST /reports` | `{targetType,targetId,reportType,description≤500?,images:string[]≤3?}` | — | `ReportController.java:48-52`；`dto/ReportCreateDTO.java:21-41` | ✅ |
| `pageMyReports` | `GET /reports/my` | `status?/pageNum/pageSize(@Max100)` | `list/total`；`targetType/targetTitle/reportType/status/statusText/result/handledAt/createdAt` | `ReportController.java:54-62`；`vo/ReportVO.java` | ✅ |

### 1.8 `api/recommend.js`（3 个）

| 函数 | 方法 + 路径 | 请求 | 响应取值 | 后端 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `recommendHome(limit=12)` | `GET /recommend/home` | `limit(1~50)` | `data` = GoodsCardVO[] | `RecommendController.java:44-51` | ✅（首页实传 8） |
| `recommendHot(limit=10)` | `GET /recommend/hot` | `limit(1~50)` | `data` = GoodsCardVO[] | `RecommendController.java:53-58` | ✅ |
| `recommendSimilar(id,6)` | `GET /recommend/similar/{goodsId}` | `limit(1~20)` | `data` = GoodsCardVO[] | `RecommendController.java:60-66` | ⚠️ **前端零调用**（详情已内联 `similarGoods`）；React 版同样不接，保接口不删 |

### 1.9 后端存在、前台未用（不要"顺手"接）

`POST /api/auth/refresh`（`AuthController.java:48-52`）、`GET /api/users/me/overview`（`UserController.java:45-48`）、`GET /api/want-posts/offers/my`（`WantPostController.java:100-107`）、`POST /api/want-posts/{id}/restore`（`:75-80`）、`GET /api/swap-posts/requests/my`（`SwapPostController.java:100-107`）、`POST /api/swap-posts/{id}/restore`（`:74-79`）。

---

## 2. 鉴权与错误码

| 码 | 含义 | 触发点 | 旧版前端行为（**新应用要等价保留**） |
| --- | --- | --- | --- |
| `40100` | 未登录/凭证失效 | `AuthInterceptor.java:89,99`、`UserContext.java:29`、`UserArgumentResolver.java:28`、`UploadController.java:43`、`WantPostServiceImpl.java:461`、`SwapPostServiceImpl.java:210` | `clearToken()` + 跳 `/login?redirect=<当前全路径>` + **仍弹一次错误提示**（旧版既跳又弹，见陷阱 #8） |
| `40101` | 用 refresh token 访问业务接口 / refresh 参数非法 | `AuthInterceptor.java:92`、`AuthServiceImpl.java:121,125,237` | 同上 |
| `40301` | 账号被封禁 | `AuthInterceptor.java:72-75`；**白名单 5 条**：`/users/me`、`/auth/logout`、`/auth/refresh`、`/notifications/unread-count`、`/chats/unread`（`:48-53`） | **弹去重确认框**（`bannedDialogOpen` 标志），**不登出**（封禁用户可查看原因/期限） |
| `40302` | 信用分受限（`<60` 或 null） | `UserAccessGuard.java:30-35,38-41`；阈值 `application.yml:50` | warning toast：`{message}（完成订单可获得信用分，恢复至 60 分后自动解除）` |
| `40309` / `40310` | 管理员停用 / 须改密 | `AdminServiceImpl.java:79,229`；`AuthInterceptor.java:80-83` | 前台不会遇到（**不要塞进登录失效分支**） |
| `50000` | 兜底系统错误（含缺必填参数） | `GlobalExceptionHandler.java:68-72` | 通用 error toast |

**前端错误分支的隐式契约**：旧版有 4 处用 `(err.message || '').includes('认证')` 判断后跳 `/verify`（GoodsDetail / GoodsPublish / WantPublish / SwapPublish）。新应用**保留跳转行为**，但实现改为判断错误码 + 文案（避免纯字符串匹配）。

---

## 3. WebSocket（聊天）

| 项 | 契约 | 证据 |
| --- | --- | --- |
| 端点 | `/ws`（**不在 `/api` 下**），`allowedOriginPatterns: *` | `config/WebSocketConfig.java:26-30` |
| 握手鉴权 | query `?token=` 优先，`Authorization` 回退；要求 `type=USER` 且 `tt=access`；失败真 **401**（与 REST 不同） | `websocket/JwtHandshakeInterceptor.java:36-45,52-57,47/55/63` |
| 客户端→服务端 | 旧前端**只连不发业务帧**（发消息走 REST）；后端支持 `{"type":"CHAT_SEND",...}` 与 `{"type":"PING"}`→`PONG` | `ChatWebSocketHandler.java:70-75,79-90,73` |
| 服务端→客户端 | `CHAT_NEW` = `{type, message: MessageVO}`（**发送方与接收方都推**）、`CHAT_READ` = `{type, conversationId, readerId}`（仅对端）、`ERROR` = `{type, code, message}`（业务码原样回传） | `ChatServiceImpl.java:299-306,308-317,319-328`；`ChatWebSocketHandler.java:96-102` |
| 通知 | **无 WS 通道**，只写库 → 红点只能 30s 轮询 | `NotificationServiceImpl.java:19-29` |
| 前端旧实现 | 退避 1000→15000ms、`onopen` 重置、`manualClosed` 阻止重连、无心跳 | `MessageCenterView.vue:388,399-424` |

---

## 4. 文件上传

| 项 | 契约 | 证据 |
| --- | --- | --- |
| 路径 | `POST /api/uploads`（注意：`vite.config.js` 里的 `/upload` 代理规则**实际未用到**） | `api/goods.js:25-27`；`UploadController.java:26,33-37` |
| form-data 字段名 | **`file`**（不得改） | `goods.js:24`；`UploadController.java:34` |
| 响应 | `{ url, thumbUrl }`；webp 不生成缩略图（`thumbUrl` 回原图） | `vo/UploadVO.java:14,17`；`UploadServiceImpl.java:127-129` |
| URL 形态 | 原图 `/upload/{yyyyMM}/{uuid}.{ext}`，缩略图 `..._thumb.{ext}`（最长边 400px） | `UploadServiceImpl.java:42,74,166` |
| 后端限制 | 单文件 5MB / 单请求 50MB；后缀白名单 → **魔数白名单**（`40000 图片内容与格式不符`） | `application.yml:7-9`；`UploadServiceImpl.java:39,57-59,96-119`；`GlobalExceptionHandler.java:49-52` |
| 数量限制 | 举报 ≤3（`ReportCreateDTO.java:40`）、商品 1~9（`40002/40003`） | `ErrorCode.java:28,30` |
| 前端限制（旧版） | 仅举报链路有 `before-upload`（MIME + 5MB）；**商品链路与头像上传都没有** | `ReportDialog.vue:112-123`；`GoodsPublishView.vue:63-72`；`ProfileView.vue:215` |

---

## 5. 必须保留的兼容点（新应用逐条照做）

1. baseURL `/api`；`/upload/**` 静态前缀；上传字段名 `file`。
2. **信封处理**：新应用在 baseQuery 统一解包 `data`（与 `frontend-admin` 一致），因此**所有调用点从 `res.data.x` 改为直接 `x`**（本表「响应取值」列即解包后的路径）。
3. token 键 `campus_market_token` / `campus_market_refresh_token` 与**双 storage + 写入清另一侧 + 读取 localStorage 优先**语义；`remember` 默认 `true`。
4. `Bearer <access>`；WS 用 `?token=`。
5. 端口 **5173**；三条代理 `/api`、`/upload`、**`/ws`（`ws: true`）** → `http://localhost:8080`。
6. `VITE_DEMO_MODE`（认证页演示提示）、`VITE_WS_URL`（可选覆盖）。
7. 路由 query 名：`redirect`、`peerUserId`、`goodsId`、`id`、`tab`、`categoryId`、`q`、`pageNum`。
8. 全部枚举字面量（见 §0.2）。
9. 分页参数名 `pageNum`/`pageSize` 与各自上限（见 §0.3）。
10. 双红点 **30s 轮询**、且**封禁账号不启动轮询**（`MainLayout.vue:99,107,112-115`）。
11. WS 协议与退避参数（1000→15000ms）。
12. 会话消息**倒序返回 → 前端反转**、**拉取即已读**、打开会话本地清零。
13. `40301` 弹窗**去重**是行为契约。
14. **10 个 `data-testid`**：`login-username`、`login-password`、`login-forgot`、`register-username`、`register-nickname`、`register-password`、`register-confirm`、`verify-student-no`、`verify-email`、`verify-code`；以及 `aria-label`：`主导航`、`搜索商品`、`搜索校园好物`、`通知`、`消息`、`隐藏密码`/`显示密码`、`微信登录`、`QQ登录`、`关闭`。
15. **全部中文文案**（提示、确认框、按钮、占位符）逐字保留 —— 多处带 `PRD §x.x` / `验收 Px` 注释，属产品需求。

---

## 6. 契约陷阱 → React 版处置

| # | 陷阱 | 处置 |
| --- | --- | --- |
| 1 | 旧版拦截器返回整个信封（所有调用点写 `res.data`） | 新应用 baseQuery 解包 `data`，**调用点成对改写并逐条对照本表** |
| 2 | 失败恒 HTTP 200，只能靠 `code` 判错 | baseQuery 内判 `code`，不要依赖 HTTP 状态 |
| 3 | 缺必填 query 参数返回 `50000` 而非 `40000` | `role`、`revieweeId` 等必填参数在调用前做前置校验，避免出现"系统繁忙" |
| 4 | `bannedUntil` 两种格式（登录 ISO / `/users/me` 空格） | 统一经 `formatDateTime()` 归一后再展示 |
| 5 | 前端无 refresh 流程，access 2h 过期掉线 | **保持现状**（不新增自动刷新，属功能变更）；40100 时正常跳登录 |
| 6 | `40101` 语义被误读 | 与 `40100` 同处理（旧版即如此） |
| 7 | WS 旧版直读 `localStorage` → `remember=false` 用户无实时消息 | **按用户确认的"可选修复"处理**：改走 `getToken()`（记录为行为变更） |
| 8 | 401 分支"既跳登录又弹提示"、只清 token | 保留等效可观察行为；同时把 `clearCredentials` 做完整（清 token + refreshToken + store 状态） |
| 9 | `pageReviews` 的 `revieweeId` 可能 undefined → 50000 | 调用前判空（未拉到 profile 时不发请求） |
| 10 | `/goods/my` 静默忽略多余参数 | 只传 `pageNum/pageSize`；过滤在前端做 |
| 11 | 标签双形状（列表 `string[]` / 详情 `[{id,name}]`） | 保留兼容取值函数 |
| 12 | images 三形状（举报 `string[]` / 商品 `[{url,thumbUrl}]`） | 按业务分别建模，不复用同一类型 |
| 13 | 上传字段名固定 `file` | `upload.ts` 固定 `formData.append('file', file)` |
| 14 | 商品 9 张上限是"先上传后判断"、无 5MB 前置校验 | 默认**照抄**（用户未勾选修复）；如修，需在 `beforeUpload` 前置校验 |
| 15 | 后端有 `PING`→`PONG`，旧前端不发心跳 | 保持一致（不发心跳）或补心跳——**默认保持一致** |
| 16 | 通知无 WS 推送 | 保留 30s 轮询 |
| 17 | `CHAT_NEW` 同时推发送方与接收方（+REST 返回值） | 消息按 `id` 去重 |
| 18 | 会话消息倒序 + 拉取即已读 | 反转 + 拉取后打已读 |
| 19 | 封禁白名单只有 5 条路径 | 新增任何全局轮询前先确认是否在白名单，否则封禁账号会刷屏 |
| 20 | `register` 多传 `confirmPassword` | 保留（后端忽略）；或在 request 层剔除——**默认保留**（与旧版一致） |

---

## 7. 待实测确认（写代码时用探针验证）

1. `api/goods.js:26` 手写 `Content-Type: multipart/form-data`（无 boundary）在浏览器下的实际行为 —— 旧版可用，新应用改用原生 `FormData` 交给 axios 自动带 boundary。
2. `confirmPassword` 被忽略所依赖的 Jackson 开关（本仓 `application.yml` 未显式配置，仅 `spring.jackson.time-zone`）。
3. `GoodsDetailVO.similarGoods`、`OrderListVO` 的 `canXxx` 五个布尔的完整字段名 —— 以运行时响应为准（探针打印）。
