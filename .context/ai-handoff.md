---
schema_version: 1
handoff_id: 20261017-123000
updated_at: 2026-10-17T12:30:00Z
from: human
to: either
status: complete
source_machine: wenling
target_machine: same
branch: none
commit: none
---

# AI project handoff

> 说明：本文件按 `shared/cross-client-handoff.md` schema 维护。schema 枚举仅有 claude|codex|human，而本工程实际使用过 ZCode / WorkBuddy / Codex / OpenCode 等多个客户端，故 `from` 一律记为 `human`，真实客户端在各条日志中注明。`to: either` 表示任意 AI 客户端均可接手。
> `branch/commit: none` —— 项目根目录**不是 git 仓库**（`backend/`、`frontend-web/` 各自独立 git；`frontend-admin/` 与 `frontend-admin-react/` 未纳入版本管理）。
>
> **最新交接任务见下方「★ 当前交接任务（2026-10-17）」（前台 React 重建）；紧随其后的「★ 当前交接任务（2026-10-16）」是后台 React 重构的验收记录，同为现状。再往下的 Outcome / Current state / … / Open questions 属于 2026-10-08 及更早的历史交接，已用分隔线标注，仅作背景参考。**
> 配套可读版详细文档：`docs/frontend-web-react-交接文档.md`（前台）、`docs/frontend-admin-react-交接文档.md`（后台）；前台验收证据 `docs/frontend-web-react-验收记录.md`。

## ★ 当前交接任务（2026-10-17，DSH / DeepSeek V4.1）— 前台 React 重建已验收

### Outcome

按用户批准方案**新建 `frontend-web-react/`**（与 `frontend-admin/` 同技术栈：Vite 6 + React 19 + TS strict + Tailwind v4 + shadcn/ui + React Router 7 + RTK Query + sonner + react-hook-form/zod），1:1 重建前台商城 13 页 + 4 详情页（编辑态复用发布路由，合计 16 路由），并**新增纸质布告栏风格落地页**；视觉统一为「新复古学院风 / 纸质布告栏」（系统字体栈 + 纸感 CSS 构图，登录页原 2.26MB 插画改为零资源拼贴）。

**硬约束全部满足**：`backend/` 未改一行；旧 `frontend-web/` **零改动**（`src` 最新 mtime 仍是 2026-10-16 的一次授权修复，今天 0 个文件被改）；**只建新目录、不替换**旧应用。

**验收结论**：演示动线 §1~§9 **38 条可自动化断言全绿**；`tsc --noEmit` 0 error；`vite build` 4.04s（首屏约 120~150KB gzip，旧 Vue 版约 450KB）；全程 0 console error / 0 pageerror。

### 交付物索引

| 类型 | 路径 |
|---|---|
| 新应用 | `frontend-web-react/`（86 文件 / 10,404 行；旧版 60 文件 / 7,106 行） |
| 使用手册 | `frontend-web-react/README.md`（技术栈/目录/契约约定/与旧版差异/环境陷阱） |
| 交接文档 | `docs/frontend-web-react-交接文档.md`（分层、每页要点、契约陷阱 10 条、如何新增页面） |
| 验收记录 | `docs/frontend-web-react-验收记录.md`（§1~§9 逐条 预期/实测/证据 + 契约探针 + 缺陷与差异登记） |
| 契约对照 | `docs/contracts-frontend-web.md`（本轮据此实现，并**修正了 2 处表内错误**） |
| 联调脚本 | `docs/联调脚本/web-react/`（`pw_multi.cjs` + `acc1~acc10.json` + `.report.json`） |
| 截图 | `gui-test-screenshots/web-react/`（57 张，70~183） |

### 本轮修掉的真实缺陷（3 条）

| # | 严重度 | 位置 | 问题 | 处置 |
|---|---|---|---|---|
| 1 | **高（致命）** | `features/msg/useChatSocket.ts` | `manualClosed` 在 React StrictMode 的 cleanup 后未复位，第二次 `connect()` 直接 return → **WebSocket 从未建立**（实时消息/已读回执/离线未读全部失效，且 REST 仍能拉历史消息、极难察觉） | effect 开头复位 `manualClosed.current = false`；用 WS 帧观测佐证（修前 `closed:true,frames:0` → 修后 `closed:false,frames:9`） |
| 2 | 高 | `api/orderApi.ts` + `pages/goods/GoodsDetailPage.tsx` | 把 `POST /orders` 当成返回 id，实际返回**整个 `OrderDetailVO`** → 下单后跳 `/orders/[object Object]` | 类型改 `mutation<OrderDetailVO, number>`，取 `created.id`；同步修正契约对照表 |
| 3 | 高 | `pages/goods/GoodsListPage.tsx` | 旧版列表只在 setup 读一次 `route.query`，页内点分类/顶栏搜索不刷新 | 改为响应 query 变化（**刻意修正**，否则演示动线 §1.4/§1.5 不过） |

### 与旧版的刻意差异（6 条，已登记在 README §差异 与验收记录 §12）

1. `/` = 落地页，商城主页迁 `/home`，Logo → `/`，脏 URL 兜底 `/home`；2. 登录成功默认跳 `/home`；3. 列表响应 query；4. WS 用 `getToken()`（支持 `remember=false`）；5. 删除/下架后回跳 `/home`；6. **保留**旧行为：`?tab=notify` 不生效、成色筛选单值生效、图片上限检查在上传之后。

### 保留的旧行为 / 已知非缺陷现象

- 热度榜/猜你喜欢在新商品未进推荐缓存时为空（`RecommendTask` 每小时重算 `heat_score`），页面渲染正确；
- `stu_dave` 处于封禁态（此前后台联调所致），40301 属正确拦截，故未认证场景改用 `stu_carol` 验证 40304；
- 未接入 refresh token 换发（前端无刷新流程，与旧版一致）。

### Acceptance criteria（已复核）

- [x] `cd frontend-web-react && npx tsc --noEmit` → 0 error
- [x] `npx vite build` → 成功（4.04s，按路由切块 + react/vendor/data/radix 手动分包）
- [x] 演示动线 §1（游客）、§2（注册+真实认证+资料+未认证拦截）、§3（发布/敏感词/0 图/教材字段）、§4（SALE 闭环+信用+2+互评）、§5（求购应约+原子接受+40915/40909）、§6（交换+40916+双确认+双方+2）、§7（双开实时+敏感词红字+商品卡+离线未读+已读回执）、§8（举报+40900+40300+我的举报）、§9（兜底推荐+相似推荐排除自身）全部通过
- [x] 旧 `frontend-web/` 零改动（mtime 证据：今天 0 个文件被改）
- [x] 文档齐备：README / 交接文档 / 验收记录 / 迁移方案同步（EP→shadcn/ui 对照表）/ handoff 更新

### Start here

1. `cd "D:\毕设\校园二手平台交易系统\frontend-web-react"` → `npm install` → `npx tsc --noEmit` → `npm run dev`（5173）
2. 依赖：MySQL 隔离实例 **3307** + 后端 **8080**（`java -jar target\campus-market-0.1.0-SNAPSHOT.jar "--spring.datasource.url=jdbc:mysql://localhost:3307/campus_secondhand?..."`）
3. **坑**：运行中新装依赖或首次访问新 chunk 后若出现 `Invalid hook call` 或新路由落 `/home`，停 dev server → 删 `node_modules/.vite` → 重启（本轮踩了 3 次）
4. 演示账号：`stu_alice`(已认证) / `stu_bob`(已认证) / `stu_carol`(未认证·受限) / `stu_react02`(本轮注册并真实认证)；密码 `Passw0rd!`；验证码看后端日志

---

## ★ 当前交接任务（2026-10-16，OpenCode / DeepSeek V4.1 Flash Free）— 已完成运行时联调验收

### Outcome

把 `frontend-admin`（Vue3 + Element Plus）重构为 React 新应用 **`frontend-admin-react`**，视觉升级为「新复古学院风 / 纸质布告栏」；**业务逻辑、接口契约、路由与 RBAC 矩阵与后端零变更**。

M0~M7 全部代码（10 个页面全部实现，非占位）由 OpenCode 会话完成；**运行时联调验收已由 2026-10-16 下午的 DSH（DeepSeek）会话执行完毕**：真实 MySQL/Redis + 后端 8080 + 前端 5174 + 无头 Chrome 实测 12 条验收清单全部通过，修掉 8 项前端缺陷（含 4 处状态语义反转），`tsc --noEmit` 与 `vite build` 复核通过。**同日按用户决定执行"替换"**：React 版接管规范目录 `frontend-admin/`，旧 Vue3 版归档为 `frontend-admin-vue-archive/`（保留可运行、一行命令可回滚）。**本任务已收口，无剩余开放环。** 详见下方「联调验收结果」与 `docs/frontend-admin-react-交接文档.md` §11/§12。

### 联调验收结果（2026-10-16 15:00~15:30，DSH / DeepSeek 会话实测）

**验收环境**（与用户真实库 3306 完全隔离；后台重构部分未改动 `backend/`、`frontend-admin/`、`frontend-web/`；**唯一例外**是事后经用户单独授权修掉了前台一处阻断登录的组件 bug，见下方日志 2026-10-16T16:30Z）
- MySQL：`D:\mysql\bin\mysqld.exe --no-defaults --datadir=<系统临时目录>\campus-mysql3307 --port=3307 --mysqlx=0 --console`（**必须后台任务方式启动**），建库脚本 `sql/schema.sql`（27 表）+ 联调种子数据（4 用户 / 4 商品 / 2 求购 / 2 交换 / 5 举报 / 12 条信用流水 / 1 轮播 / 2 公告）
- 后端：`cd backend && java -jar target\campus-market-0.1.0-SNAPSHOT.jar "--spring.datasource.url=jdbc:mysql://localhost:3307/campus_secondhand?..."`，并用 `ADMIN_USERNAME=superadmin / ADMIN_PASSWORD=Admin@12345` 触发 `AdminInitializer`（首登强制改密）
- 前端：`cd frontend-admin && npm run dev`（5174；2026-10-16 起规范目录名，原 `frontend-admin-react`）
- 浏览器：**无头 Chrome + CDP + Playwright（`chromium.connectOverCDP`）**。沙箱内 Chrome 无法启动（Mojo 命名管道被拦），需以 danger-full-access 方式拉起 `chrome.exe --headless=new --remote-debugging-port=9222 --user-data-dir=<临时目录>`；截图证据在 `gui-test-screenshots/admin-react/`（48 张，编号 01~50 缺 18/19）

**12 条验收清单实测结果（全部 ✅）**
1. ✅ `npx tsc --noEmit` 无错误；`npx vite build` 成功（5.95s，dist 产物正常）。注意：沙箱内 vite build 会因 esbuild 管道 `spawn EPERM` 失败，需在非受限模式下跑。
2. ✅ super 登录 → `/dashboard`；`/auth/me` 恢复会话，**刷新页面不跳登录**。
3. ✅ 三角色实测 RBAC：super=全部；auditor 菜单仅 大屏/内容/举报/用户/敏感词/日志；operator 仅 大屏/分类/运营/日志。越权路由（auditor→`/category`、operator→`/user`）重定向 `/dashboard` + toast「当前角色无权访问该页面」。API 层复核 40300 一致。
4. ✅ `mustChangePassword=1`：登录后被不可关闭弹窗拦住（Esc 无效、无关闭按钮、无遮罩误关）；`min 8 位` 与 `两次不一致` 校验实测生效；改密成功才进后台。**并修复**：改密标记会话刷新后会绕过门禁（详见修复清单 #8）。
5. ✅ 10 个页面逐一走查：分页（95→5 页、97→5 页）、状态/关键词筛选、重置、详情弹窗/抽屉、确认框、**写操作后列表自动刷新**（RTK Query 标签失效）全部正常；10 路由最终回归 **0 console error / 0 page error**。
6. ✅ 内容三门禁实测：`ON_SALE` 仅下架可用、`OFF_SALE` 仅恢复可用、`IN_TRANSACTION`/`DELETED` 删除禁用；下架/恢复后列表即时刷新且按钮态翻转正确。
7. ✅ 举报工作台：证据图（后端 `List<String>` 数组）2 张全部真实加载（naturalWidth=900）；`targetType=USER` 时「下架内容」禁用并标注「不适用」；组合处置（警告+扣信用分）与驳回（确认→理由→提交）后列表刷新，操作日志落 `REPORT_HANDLE`（detail 含动作串）。
8. ✅ 用户管理：信用分按钮仅 super 可点（auditor 实测 `disabled + title="仅超级管理员可调整信用分"`）；封禁 7 天/解封生效并即时刷新；详情抽屉信用流水分页可用（12→13 条，±分与中文 reason 正确）。
9. ✅ 运营配置：轮播上传走 `/api/uploads`（带 Bearer）返回 `/upload/202610/<uuid>.png`，表单预览与列表缩略图均真实加载；公告 草稿→发布→下线 全流程正确，且**已下线公告不再显示「发布」按钮**（修复清单 #5）。
10. ✅ 敏感词库：95 条分页（20/页）、关键词筛选、新增、删除、批量导入去重正常；导入 toast 改为使用**后端返回的实际新增条数**（修复清单 #6）。
11. ✅ 管理员管理：新建（初始密码 ≥8 校验、创建后为 `mustChangePassword=1`）、启停（状态位与文案已纠正）、重置密码（重置后标记须改密）全部实测通过。
12. ✅ 旧 `frontend-admin` 替换/并存 —— **已按用户决定执行"替换"**（2026-10-16）：React 版接管 `frontend-admin/`，Vue 版归档 `frontend-admin-vue-archive/`（含 `README-ARCHIVE.md` 与一行回滚命令），替换后已复验 5174 登录/会话/页面/代理全绿（§12.3）。

**本轮实测修正的前端缺陷（8 项，全部只改前端）**
| # | 严重度 | 文件 | 问题 | 处置 |
| --- | --- | --- | --- | --- |
| 1 | 高 | `pages/admin/AdminManagePage.tsx` | 管理员启停状态语义反转（后端 0=启用）：启用的账号显示「已停用」、按钮/确认框动作与效果相反 | 引入 `ADMIN_STATUS_ENABLED/DISABLED` 常量并全量纠正文案、按钮、编辑弹窗选项 |
| 2 | 高 | `pages/user/UserManagePage.tsx` | 用户封禁状态反转（后端 1=封禁）：正常用户显示「已封禁」并给「解封」按钮 | 引入 `USER_STATUS_NORMAL/BANNED` 并纠正列表徽标与详情抽屉 |
| 3 | 高 | `pages/user/UserManagePage.tsx` | `authStatus` 映射错误（0/1 才有定义，旧映射把已认证显示成「审核中」） | 改为 `{0:未认证, 1:已认证}` |
| 4 | 高 | `pages/category/CategoryManagePage.tsx` | 分类/标签启停反转（后端 0=启用）：全部启用项显示「已禁用」，编辑开关写反 | 引入 `CATALOG_STATUS_ENABLED/DISABLED`，纠正徽标、开关 `checked` 与提交值 |
| 5 | 中 | `pages/operation/OperationConfigPage.tsx` | 已下线公告（status=2）仍显示「发布」按钮，必然失败（后端仅 0→1） | 仅 status=0 显示「发布」、status=1 显示「下线」、status=2 不显示 |
| 6 | 低 | `pages/sensitive-word/SensitiveWordPage.tsx` | 批量导入 toast 用前端提交数，与后端实际新增数不符 | 使用接口返回的实际新增条数 |
| 7 | 中 | `pages/dashboard/DashboardPage.tsx` | 折线卡片按固定 7 桶（周一~周日）取数，而后端只返回「周一→今天」，导致周四之后恒为 0、标题语义失真；且两张图各占 1/5 栅格，一行空出 3/5 | 按返回数组长度对齐标签、标题改为「本周新增订单趋势」；改为 `lg:col-span-3` + `lg:col-span-2` |
| 8 | 中 | `router/guards.tsx` | `mustChangePassword` 仅在登录页消费：携带该标记的会话刷新后可直进后台，之后每个请求都被 40310 拦（仅弹 toast） | `RequireAuth` 检测到标记 → toast 并 `Navigate` 回 `/login` 重新走门禁 |

**期间观测到的后端侧缺陷（受"不改后端"约束，仅记录不修）**
- **举报列表 `targetTitle` 跨类型串号**：`AdminReportServiceImpl.buildVOs` 把 goods/want/swap/user 的标题合并进同一个 `Map<Long,String>`（仅以 id 为键），id 冲突时被后写的类型覆盖。UI 现象：GOODS 工单 #1 的「被举报内容」显示成 `want_post#1` 的标题（详情接口单条解析所以正确）。属后端既有缺陷，`frontend-admin`（Vue 版）同样存在，本轮不改。
- 管理体系统计口径：`creditDistribution` 为硬编码中文键（优秀/良好/一般/受限），且 `<=59` 不含 NULL 信用分，四桶之和未必等于 `userTotal`（本轮实测 4 = 1+2+0+1，正常）。
- `favicon.ico` 404（本轮已用内联 SVG data-URI 补齐，消除 console 噪声）。

**证据与产物**
- 截图：`gui-test-screenshots/admin-react/01~50-*.png`（48 张，含登录/门禁/大屏/内容/举报/用户/分类/运营/敏感词/日志/管理员/RBAC/越权 toast/修复前后对比）
- 联调脚本（已随工程留存到 `docs/联调脚本/`）：接口契约探针 `verify_api.mjs` / `verify_operator.mjs`，浏览器剧本驱动 `pw_driver.cjs` + `act1~act20.json` + `rep1~rep20.json`，联调种子 `seed_admin_ui.sql`
- 接口契约实测结论：`AdminLoginVO` 扁平且 `role` 小写、`mustChangePassword` 为布尔；`AdminVO.mustChangePassword` 为 0/1 数字；`CreditLog.scoreChange`；`ReportVO.images` 为 JSON 数组；`Result{code,message,data}` + HTTP 恒 200；时间格式 `yyyy-MM-dd HH:mm:ss`；敏感词/日志/用户/账号均为 `PageResult` —— 与 `src/api/types.ts` **全部一致，无需改类型**。

### Acceptance criteria（联调后复核）

- [x] `cd frontend-admin && npx tsc --noEmit && npx vite build` 均通过（构建 5.95s）
- [x] super 账号登录成功并落到 `/dashboard`；**刷新页面不跳登录**（验证 `/auth/me` 恢复 + 守卫）
- [x] 三角色（super/auditor/operator）分别登录：侧栏仅显示矩阵内菜单；手动访问越权路由被重定向回 `/dashboard` 并提示
- [x] `mustChangePassword=1` 账号：登录后被改密弹窗拦住，改密成功后才进入后台（含刷新绕过漏洞修复）
- [x] 10 个页面逐一走查：分页、筛选、重置、弹窗、确认框、**写操作后列表自动刷新**（RTK Query 标签失效）
- [x] 内容管理三条门禁正确：下架（`GOODS=ON_SALE` / `WANT|SWAP=OPEN`）、恢复（`GOODS=OFF_SALE` / `WANT|SWAP=CLOSED`）、删除（`GOODS=IN_TRANSACTION` 与已 `DELETED` 禁删）
- [x] 举报工作台：证据图正常渲染（后端实为 JSON 数组，`parseImages` 双形态兼容）；`targetType=USER` 时「下架内容」禁用；处置与驳回后列表刷新
- [x] 用户管理：信用分按钮仅 super 可点（其他角色禁用+提示）；封禁/解封生效；详情抽屉信用流水分页可用
- [x] 运营配置：轮播图上传走 `/api/uploads` 且带 Bearer，返回 url 可预览；公告草稿→发布→下线流程正确
- [x] 敏感词库：筛选、分页、新增、删除、批量导入（去重）正常
- [x] 管理员管理：新建（初始密码 ≥8 位）、编辑（姓名/角色/启停）、重置密码正常
- [x] 旧 `frontend-admin` 替换/并存方案落地 —— 已执行"替换"并复验通过（§12）

### Current state（接手前已完成，供追溯）

**OpenCode 会话（上午）**
- 新建 `frontend-admin-react/`：Vite 6 + React 19 + TS(strict) + Tailwind v4(CSS-first) + shadcn/ui(Radix, new-york) + React Router 7(Data Router) + Redux Toolkit/RTK Query + Recharts + sonner + react-hook-form/zod。
- 设计系统 `src/styles/index.css`：复古森林绿 `#2E5B4E` + 暖米白 `#F7F2E9` + 姜黄 `#D89A3C` + 砖红 `#B4443A`；纸面颗粒、`.paper-card` / `.paper-fold` / `.dashed-card` / `.tape-tag` / `.stamp-badge` / `.topbar-blur`；半径 14px。
- 22 个 shadcn/ui 原语 + 业务共享组件（`PageHeader` / `StatusBadge` / `DateTimeText` / `EmptyState` / `TableSkeleton` / `Pagination` / `PageLoader` / `useConfirm` 替代 `ElMessageBox.confirm` / `usePrompt` 替代 `ElMessageBox.prompt`）。
- 数据层：`baseApi`（单 reducerPath + `injectEndpoints`）+ 自定义 axios baseQuery（统一解包 `{code,message,data}`；`code!==0` 返回 error 并 toast；`40100/40101` 或 HTTP 401 → 清空登录态）+ 8 个 api 分片 + `upload.ts`（完整路径 `/api/uploads`，手动带 Bearer）。
- `authSlice`（token 持久化键 `campus_market_admin_token`，管理员信息内存态）+ `RequireAuth` + `RequireRole`。
- **10 个页面全部实现**，路由级代码分割 + 手动分包（charts 349KB / react 327KB / vendor 312KB / radix 92KB / data 99KB；各页面 3~12KB）。
- `frontend-admin-react/README.md` 已写。

**替换后仍未做（非阻塞，留待用户/后续会话）**
- ❌ 未纳入版本管理（`frontend-admin/` 目录非 git 仓库；`frontend-admin-vue-archive/` 同样未纳入，与替换前一致）。
- ❌ 死代码未清理：`src/pages/PlaceholderPage.tsx` 已无引用；`radio-group` / `scroll-area` / `avatar` / `tooltip` 原语已建暂未使用；`useGetReportDetailQuery` 已声明未调用（与 Vue 版行为一致）。
- ❌ 归档目录仍在磁盘上（含 `node_modules` 约 170MB）；确认不再需要时可整体删除。

**阻塞项**：无。

### Start here

1. `cd "D:\毕设\校园二手平台交易系统\frontend-admin"` → `npm install` → `npx tsc --noEmit` → `npm run dev`（5174）
2. 启动后端依赖与后端（8080）：**MySQL 用隔离 3307 实例（勿用用户真实库 3306，其 root 密码未知）**，命令见本文「验收环境」；冒烟 `GET http://localhost:8080/api/health` 应返回 `{code:0,...}`
3. 复用 `gui-test-screenshots/admin-react/` 中的截图对照走查；如需重跑浏览器剧本，用 `chromium.connectOverCDP` 连接手动拉起的无头 Chrome（沙箱内 Chrome 起不来），剧本与断言报告见 `docs/联调脚本/`
4. 旧 Vue 版在 `frontend-admin-vue-archive/`（**勿改**）；需要回滚时按其 `README-ARCHIVE.md` 的两条 `Rename-Item` 执行
5. 全程**不得修改 `backend/` 与 `frontend-web/`**

### Files and commands

- `frontend-admin-react/` — 本轮新增的 React 后台应用（全部为新增文件，可安全编辑）
  - `src/api/types.ts` — 全部后端契约类型（`Result` / `PageResult` / 各 VO）
  - `src/api/baseQuery.ts` — 统一解包 + toast + 40100/40101 处理（错误语义的中枢）
  - `src/api/request.ts` — axios 实例（`baseURL=/api/admin`，注入 Bearer）
  - `src/api/upload.ts` — `POST /api/uploads`（非 RTK Query，手动带 token）
  - `src/router/routes.tsx` / `src/router/guards.tsx` / `src/router/nav.ts` — 路由、守卫、菜单与权限
  - `src/styles/index.css` — Tailwind v4 主题令牌与纸感样式
  - `src/layouts/AdminLayout.tsx` — 侧栏 + 顶栏 + 账户下拉/登出
  - `src/pages/**` — 10 个页面
- `docs/frontend-admin-react-交接文档.md` — 可读版详细交接（接口对照表、验收清单、风险）
- `frontend-admin-react/README.md` — 技术栈、启动方式、契约与行为保留清单
- `frontend-admin/` — 旧 Vue 应用，**原样保留作为回滚路径，本轮未改动**
- `backend/src/main/java/com/campus/market/controller/` — 接口契约的唯一真源（前端已逐条比对）
- Build: `cd frontend-admin && npx vite build`
- Typecheck: `cd frontend-admin && npx tsc --noEmit`
- Dev: `cd frontend-admin && npm run dev`（5174，代理 `/api`、`/upload` → 8080）

### Decisions and guardrails

- 技术栈锁定（用户拍板）：React + TypeScript + Vite + Tailwind v4 + shadcn/ui + React Router + Redux Toolkit/RTK Query；图表用 Recharts（替代 ECharts）。
- 视觉方向锁定：新复古学院风 / 纸质布告栏（森林绿主色 + 暖米白纸底 + 姜黄点缀 + 大圆角 + 纸感/胶带/图章质感）。**纸感装饰只允许出现在侧栏、顶栏、登录页、卡片头、状态标签、空态、指标卡；表格与表单正文必须保持中性高对比**（后台可读性优先）。
- **后端零改动**：接口路径、请求/响应结构、`ErrorCode`、RBAC 矩阵、路由路径、端口 5174、`/api` 与 `/upload` 代理全部保持。
- token 存储键保持 `campus_market_admin_token`（与旧版兼容，可平滑替换）。
- 错误提示沿用旧语义：每个失败请求 toast 一次（在 `baseQuery` 统一处理，等价于原版 axios 拦截器的 `ElMessage.error`）。
- `mustChangePassword=1` 的 ADMIN 由后端拦截器限制只可访问 `/api/admin/auth/**`，前端用不可关闭弹窗配合（禁 Esc、禁点外遮罩）。
- 角色口径一律小写 `super` / `auditor` / `operator`。
- 不修改 `backend/` 与 `frontend-web/`；不擅自为工程建 git 仓库（用户此前对 `frontend-admin` 的结论是「暂不纳入版本管理」，沿用）。

### Working tree

- 应用目录（2026-10-16 起）：`frontend-admin/**` —— React 版管理后台（Vite 6 + React 19 + TS + Tailwind v4 + shadcn/ui + RTK Query，10 页面 + 22 个 ui 原语 + 8 个 api 分片 + 设计系统）
- 归档目录（勿改）：`frontend-admin-vue-archive/**` —— 替换前的 Vue3 + Element Plus 版，原样保留（含 `node_modules`），新增 `README-ARCHIVE.md`
- 修改（2026-10-16 联调会话，**全部在 React 应用内 + 文档**）：
  - `frontend-admin/src/pages/admin/AdminManagePage.tsx`、`src/pages/user/UserManagePage.tsx`、`src/pages/category/CategoryManagePage.tsx`（状态语义反转修复）
  - `frontend-admin/src/pages/operation/OperationConfigPage.tsx`（公告发布按钮门禁）
  - `frontend-admin/src/pages/sensitive-word/SensitiveWordPage.tsx`（导入计数取后端返回值）
  - `frontend-admin/src/pages/dashboard/DashboardPage.tsx`（本周趋势按实际长度 + 栅格 3/5 + 2/5）
  - `frontend-admin/src/router/guards.tsx`（强改密标记刷新后回落登录页）
  - `frontend-admin/src/lib/format.ts`（注释更正：后端时间格式实为 `yyyy-MM-dd HH:mm:ss`）
  - `frontend-admin/index.html`（内联 SVG favicon，消除 console 404）
  - 文档：`README.md`、`模块清单.md`、`docs/frontend-admin-react-交接文档.md`、`docs/演示动线.md`、`docs/前端shadcn迁移方案.md`、`frontend-admin/README.md`、`frontend-admin-vue-archive/README-ARCHIVE.md`
- 新增证据：`gui-test-screenshots/admin-react/*.png`（48 张联调截图 + 2 张替换后复验截图）、`docs/联调脚本/`（剧本 + 断言报告 + 接口探针 + 联调种子 SQL）、`frontend-admin-vue-archive/README-ARCHIVE.md`
- 未改动：`backend/**`（工作区内 3 个 M 文件为 2026-10-05 起并行的既有 WIP，非本会话产生）、`sql/**`
- 改动（**前台 frontend-web，事后经用户单独授权**）：`frontend-web/src/components/shadcn/Checkbox.vue` —— 修正 reka-ui API 漂移导致协议勾选框失效、前台无法登录的阻断性缺陷（2 行，见日志 2026-10-16T16:30Z）
- Uncommitted work ownership: 全部为本会话/上一会话新增或修改文件，接手方可安全编辑；**项目根目录不是 git 仓库**，无版本保护，建议先手工备份（或在用户同意后 `git init`）
- 运行态（本机后台进程会被回收，需时隔重启）：3307 隔离 mysqld、8080 后端（连 3307）、5174 前端 dev server（从 `frontend-admin/` 启动）；9222 为联调用无头 Chrome（可随时关闭）

### Open questions

- 是否给 `frontend-admin/` 建 git 仓库？（用户此前结论是「暂不纳入版本管理」，沿用；现旧版已归档，建仓后可只跟踪 React 版）
- 归档目录 `frontend-admin-vue-archive/`（约 170MB）确认不再需要后是否删除？
- 是否交付「设计令牌 / 组件预览页」作为 M0 验收物？（此前提出，用户未答复；建议做一页不挂菜单的 `/__styleguide`）
- 敏感词默认每页条数：后端默认 50（max 200），前端现用 20 并支持 10/20/50/100 —— 是否统一为 50？（**未改动，等用户决定**）
- 举报「扣信用分」是否需要参数化分数？（后端 `handle` 仅收 `actions[]`，扣分幅度由后端决定，建议前端不引入分数字段）
- 分类是否需要三级？（后端仅支持两级）
- 死代码清理：`pages/PlaceholderPage.tsx`、未使用的 4 个 ui 原语、未调用的 `useGetReportDetailQuery` —— 删除还是留待启用？
- 是否修复后端「举报列表 targetTitle 跨类型串号」缺陷？（需改 `AdminReportServiceImpl.buildVOs`，本轮按约束未动后端）

---

# 历史交接内容（2026-10-08 及更早，保留作背景）

## Outcome
当前会话（2026-10-08，Codex）：按用户确认，优化 `frontend-web` 学生端商城的视觉设计；首轮范围为共享主题与导航、首页、商品卡、列表及详情，保持现有业务接口与权限流程。验收：生产构建通过，桌面/手机无横向溢出，搜索/分类/商品跳转与发布登录守卫可用。下方原开发目标与历史记录保留作为背景。

完成毕业设计「校园二手交易系统」**2026.11 里程碑的核心模块开发**：① 按《数据库设计文档.md》v1.1 落地实体层（27 张表对应 Entity + Mapper，遵循 `docs/命名规范.md`）；② 用户模块 USR-01~06（注册/登录/登出/校园认证/个人主页/资料编辑）；③ 商品模块 GDS-01~08（发布/管理/列表/搜索/详情/收藏）。接口路径与响应结构遵守既有约定（`/api/**`、`{code,message,data}`、`ErrorCode` 分段）。不得扩展需求范围。

## Current state
- 已完成：
  - **需求基线**：`需求规格说明书.md` v1.0（12 项决策、约 45 条功能需求）。
  - **数据库设计 v1.1（用户修订定稿）**：`数据库设计文档.md`（27 表字段级 + ER 图；v1.0 推断经用户 9 条意见修订，见变更记录）+ `sql/schema.sql`，**实机 MySQL 8.0.44 重建验证通过**（27 表、41 条物理外键、ngram FULLTEXT、种子 10；行为测试全过：FK 拦截、待处理应约唯一、VIEW 同日去重、取号 1→2→3、回滚干净）。`order` 因 MySQL 保留字改名 `order_info`。
  - **工程骨架（2026-10-01 深夜完成并验证）**：
    - `backend/`：Spring Boot 3.5.16 单体（JDK 21 运行、按 17 编译）；分层包 common/config/security/controller/service/mapper/entity/dto/vo；`Result`/`ErrorCode`/全局异常；JWT（jjwt 0.13，access 2h + refresh 7d）+ `@RequireAuth`/`@RequireRole` 注解 + `AuthInterceptor` + `UserContext` + `@CurrentUser`；`RedisService`（仅基础命令，兼容本机 Redis 3.0）；`CreditProperties` 信用常量（对齐 PRD §5.7）；`/upload/**` 本地图片静态映射；Knife4j 4.5 + springdoc 2.8.17（Boot 3.x 兼容线）。`mvn -DskipTests package` 成功；启动冒烟通过（`/api/health` 返回 `{code:0,...}`、`/doc.html` 200）。
    - `frontend-web/`（前台）：Vue 3.5 + Vite 5 + Pinia 3 + Element Plus；PRD §7.1 全部 13 页路由 + MainLayout + axios 封装（401 跳登录）+ 登录页可交互；`npm run build` 通过。
    - `frontend-admin/`（后台）：10 页路由 + AdminLayout + RBAC 路由守卫（meta.roles 对齐 PRD §4.2）+ 独立 axios 实例（`/api/admin`）；`npm run build` 通过。
    - 根：`README.md`（启动说明 + 建库步骤）、`docs/命名规范.md`（全工程命名约束，已与 schema.sql 对齐）、`.gitignore`（git 仍未 init，属用户决定）。
  - **M1+M2 与 M3+M4（2026-10-02）**：M1 用户与校园认证、M2 商品中心验收通过并推送；M3 交易与信用（订单状态机/每日取号/互评/信用分/超时任务）、M4 即时消息（WebSocket 私信/会话未读/站内通知）开发完成、E2E 通过，已提交 `env` 并推送 GitHub（backend `91300ae` / frontend-web `a0543a7`）。
  - **M5 特色板块与推荐（2026-10-02 下午）**：求购（REQ-01~04）/ 交换（SWP-01~03）/ 推荐（REC-01~03）后端 + 6 个前台页面全部完成，API 验收 87/87 通过，**未提交（等 review）**。
  - **M7 收尾与联调（2026-10-02 晚，4 个子 agent 并行 + 主代理统一验收）**：举报（RPT-01/02）后端 + 前台举报入口/我的举报 Tab/受限封禁全局提示 + 孤儿数据扫描（T3）+ 全局拦截复查 + 需求覆盖核对表与演示动线。**M7 API E2E 52/52 通过、后端单测 191 项全绿、前台构建通过**；**未提交（等 review）**。交付物见 `.workbuddy/memory/2026-10-02.md` 与 `docs/M7-需求覆盖核对表.md`、`docs/演示动线.md`。
- 进行中：**M6 管理后台**（由另一 agent 并行开发；已提交 `f64c80c feat(admin-auth): M6 后台登录/账号管理/强制改密门禁 + 操作日志基础设施（AOP 审计）`，后台 10 个页面仍多为占位）。
- 阻塞项：无（M5/M7 验收均用隔离临时 MySQL 3307，与用户真实库 3306 隔离；真实库 root 密码仅用户掌握，未使用）。
- 环境提示（2026-10-02 下午实测）：MySQL 服务/实例默认停止 → 需要时以控制台方式拉起（`D:\mysql\bin\mysqld.exe --console` 或服务方式，服务方式需管理员）；**用 Bash 工具直接后台启动的 mysqld 会在该次工具调用结束后被回收，必须用后台任务方式启动**；8080 端口当前空闲（M5 演示用），18080 上仍挂着 M3/M4 时期启动的旧代码实例（PID 32268，其数据库已停，建议重启或关闭）。注意：① 本机 8080 端口常被其他程序占用，冒烟用 18080 验证过；② MySQL80 服务默认停止，启动需管理员权限（前一会话用控制台方式拉起 mysqld 成功）；③ Element Plus 全量引入致主 chunk >500KB（可后置优化）。
- 环境实测：JDK 21.0.12、Node 20.20、Maven 3.9.16、MySQL 8.0.44（`D:\mysql\bin`，root 密码仅用户掌握）、Redis 3.0.504（决策：只用基础命令不升级）。

## Acceptance criteria
- [ ] `entity/` 含 **27 个实体类**（对应 schema.sql v1.1 全部表，tag/goods_tag/goods_want/order_no_seq 同步落地，`order_info`→`OrderInfo`），`mapper/` 含对应 BaseMapper 接口；MP 分页插件可用
- [ ] USR 模块：注册（BCrypt）/登录（JWT，失败 5 次锁 10 分钟）/校园认证（验证码 Redis、演示模式打印控制台）接口可用并有 Knife4j 文档
- [ ] GDS 模块：发布（敏感词校验）、列表（分页+筛选+排序）、详情、收藏接口可用
- [ ] 前台登录/注册/认证页与商品列表/详情/发布页完成真实接口对接
- [ ] 全部接口遵守 `{code,message,data}` 与 `ErrorCode` 分段；代码命名通过 `docs/命名规范.md` 自查
- [ ] 用户验收：前台可注册→认证→发布商品→列表看到商品（演示模式，无真实邮件）

## Start here
1. 读 `需求规格说明书.md` §6.1/§6.2、`docs/命名规范.md`、`数据库设计文档.md`（对应表结构），再看 `backend/src/main/java/com/campus/market` 现有骨架
2. 先落地 `entity` + `mapper`（可用 MyBatis-Plus 代码生成或手写），再做 AuthService/UserService + UserController，最后 GDS 模块
3. 联调验证：`cd backend && mvn spring-boot:run`（必要时 `--server.port=18080`）；前台 `npm run dev`；跑通 Acceptance 最后一条的用户旅程
4. 通过后下一阶段 = 2026.12 交易闭环（订单状态机 + 聊天 + 互评与信用分）

## Files and commands
- `需求规格说明书.md` — 唯一需求基线 v1.0；变更需同步更新其 §1 决策记录
- `数据库设计文档.md` / `sql/schema.sql` — 库 `campus_secondhand`，27 表（v1.1）；重置建库：先 DROP DATABASE IF EXISTS campus_secondhand; 再 `mysql -u root -p < sql/schema.sql`
- `docs/命名规范.md` — 工程/命名规范（含角色大小写口径：DB 大写 SUPER/AUDITOR/OPERATOR，代码与前端一律小写，登录签发时由后端转小写）
- `前端设计文档.md` — 前端唯一设计契约 v1.1（Token/布局/23 页线框/交互规范/质量基线），已对齐双应用骨架；**tokens.scss 落地是模块①前置任务**
- `backend/` — Build: `cd backend && mvn -B -DskipTests package`；Run: `mvn spring-boot:run`（8080 被占时 `--server.port=18080`）；验证：`/api/health`、`/doc.html`
- `frontend-web/`、`frontend-admin/` — Dev: `npm run dev`（5173/5174，已代理 /api 与 /upload 到 8080）；Build: `npm run build`
- `.context/ai-handoff.md` — 本握手；`README.md` — 启动总览
- 版本管理：`backend/` 与 `frontend-web/` 各自独立 git 仓库，分支 main + dev + master + **env（当前工作分支）**。**最新（2026-10-02 下午，WorkBuddy 会话）：M3+M4 已在 `env` 分支提交并推送**——backend `env`=91300ae（含 f542c72 `fix(goods)` + 91300ae `feat(order,chat)`）、frontend-web `env`=a0543a7，工作区干净、与 origin/env 同步。推送命令（**实测有效，勿自行简化**）：
`GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never git -c credential.helper= -c credential.helper=manager -c http.proxy=http://127.0.0.1:7890 -c https.proxy=http://127.0.0.1:7890 push --porcelain origin env`
——① 必须走本机代理 127.0.0.1:7890（git 未配代理、直连被重置）；② **必须先用 `-c credential.helper=` 清空系统 helper 再指定 `manager`**（系统 helper-selector 在本环境无 tty 时会挂起，表现为 push 长时间无输出/被 kill —— 曾因此卡死 4 分钟）；③ 凭据由 Windows 凭据管理器提供（GCM 已存 ggwenling 的 PAT），无需环境变量。若 push 卡死：`ps -W | grep git` 取 **WINPID（第 4 列）** 后 `taskkill //F //PID <winpid>`。backend → `github.com/ggwenling/campus-secondhand`，frontend-web → `github.com/ggwenling/campus-secondhand-web-vue`；两仓库各有独立 .gitignore。**frontend-admin 暂未纳入版本管理**（用户决定之后再说，勿擅自建库）；提交信息遵循 `docs/命名规范.md` §5
- M1+M2 验收记录（2026-10-02 上午，ZCode 会话执行）：API 验收 68 项断言全过（含 D1 修复运行时验证：重新上架 6 项、敏感词 40001 并提示命中词）；GUI E2E 注册→认证→发布表单→列表/详情/收藏/首页走通（截图在 `gui-test-screenshots/`，图片上传 GUI 环节受浏览器自动化限制由 API 覆盖）；mvn package + npm build 全绿；演示库测试数据已 SQL 清空（0 商品 0 用户，10 种子分类，95 敏感词）。**遗留项（用户知情、暂缓）**：requireCertified 反射 hack + 重复查库、creditScore 受限策略未接线、ProfileView"我的商品/收藏"Tab 占位、登录按 IP 限流未做、"27 实体"口径与实际 12 实体不符、D3 全部不做；`m2tool/M2TestTokenMain.java` 已按用户决定随 env 提交

## Decisions and guardrails
- 技术栈锁定：Spring Boot 3.5.16 + JDK 17 编译（本机 JDK 21 运行）+ MyBatis-Plus 3.5.17（**分页插件需单独引 `mybatis-plus-jsqlparser`**，pom 已含）+ MySQL 8 + Redis（仅基础命令）+ Vue 3 双应用 + Element Plus + ECharts；**单体架构**，禁止微服务/MQ/ES
- **前端为两个独立应用**：frontend-web（前台）/ frontend-admin（后台，独立登录 + RBAC）（用户拍板）
- 包名 `com.campus.market`，artifactId `campus-market`（用户拍板）
- 鉴权：JWT + `@RequireAuth`/`@RequireRole` 注解 + 拦截器（不用 Spring Security）；refresh token 不可访问业务接口；接口文档 Knife4j + springdoc 2.8.x（勿升 3.x，仅支持 Boot 4）
- 数据口径（来自设计文档）：库名 `campus_secondhand`；**不物理删除**（goods/want_post/swap_post 用状态值 DELETED 软删）；核心关系 41 条物理外键（RESTRICT/RESTRICT，含 STORED 生成列的表禁用 CASCADE 动作），多态/审计/跳转保持逻辑外键 + 孤儿扫描；`order` → `order_info`
- 交易模式锁定：线下面交，平台不经手资金；求购/交换/出售共用订单状态机（类型字段区分）
- 信用规则常量集中在 `CreditProperties` + application.yml `app.credit`，与 PRD §5.7 一致
- 演示模式 `app.demo-mode=true`：验证码打印控制台；prod 配置全部走环境变量（MYSQL_PASSWORD/JWT_SECRET 等），dev 的 JWT_SECRET 为演示默认值
- **硬约束**：PRD §11 Out-of-Scope 十项一律不做；未确认细节按 §12 默认值执行

## Working tree
- 2026-10-08 接收核对：frontend-web `env` / `66edff8`，已有 `src/stores/user.js` 修改；backend `env` / `52a42f3`，已有 GlobalExceptionHandler、OperationLogController、OrderServiceImpl 修改。以上为既有用户/其他会话工作，不属于本轮设计改动。
- Changed files: `backend/**`、`frontend-web/**`、`frontend-admin/**`（均为本轮骨架新增）；`README.md`、`docs/命名规范.md`、`.gitignore`（本轮新增）；`数据库设计文档.md`、`sql/schema.sql`（上一环产出，勿改）；`需求规格说明书.md` 未改动
- Uncommitted work ownership: backend/frontend-web 为独立 git 仓库（初始提交已推送 GitHub）；frontend-admin 及根目录文档无版本管理；骨架文件由用户与 AI 共有，可安全编辑；数据库设计两份文件待用户审阅（重点 12 条设计推断 T1~T12），审阅前勿改表结构

## Open questions
- Element Plus 全量引入的主 chunk >500KB——是否需要按需引入/分包优化（可后置）
- 数据大屏具体图表选型（PRD §12.2 留到开发阶段定）
- 是否需要从需求文档裁剪"开题报告"版本摘录（用户此前未答复）

## Handoff log

- `2026-10-16T12:00:00Z` — `opencode`(DeepSeek V4.1 Flash Free) set status to `ready`: **frontend-admin 的 React 重构（frontend-admin-react）代码全部落地并通过静态验证，移交运行时联调与切换验收**。本会话产出：新建 `frontend-admin-react/`（Vite 6 + React 19 + TS strict + Tailwind v4 + shadcn/ui + React Router 7 + RTK Query + Recharts + sonner + react-hook-form/zod），落地新复古学院风设计系统（`src/styles/index.css`：森林绿 #2E5B4E / 暖米白 #F7F2E9 / 姜黄 #D89A3C；纸感卡、折角、虚线布告卡、胶带标签、图章徽标），实现 22 个 shadcn 原语与共享组件（含 `useConfirm` / `usePrompt` 替代 ElMessageBox），数据层含自定义 axios baseQuery（统一解包 `{code,message,data}`、失败 toast、`40100/40101` 清登录态）、8 个 api 分片、`upload.ts` 走完整路径 `/api/uploads` 带 Bearer；守卫 `RequireAuth`（刷新经 `/auth/me` 恢复角色）+ `RequireRole`；**10 个页面全部实现**（登录含强制改密门禁、数据大屏、内容管理、举报工作台、用户管理、分类管理、运营配置、敏感词库、操作日志、管理员管理），并做路由级代码分割与手动分包。验证：`npx tsc --noEmit` 无错、`npx vite build` 成功（16s）、`GET http://localhost:5174/login` → HTTP 200（后台 dev 任务运行中）、`npm install` 成功（225 包，registry=npmmirror）。**接口契约已逐条比对后端源码确认无误**（AdminAuth/AdminAccount/AdminUser/AdminReport/AdminContent/AdminCatalog/AdminOps/OperationLog/Upload 九个 Controller 的路径、请求体、query 参数，对照表见 `docs/frontend-admin-react-交接文档.md` §4）。**未做任何运行时联调（无真实登录、无接口调用、无 UI 走查）**；旧 `frontend-admin` 未改动（保留为回滚路径）；`frontend-admin-react` 未纳入版本管理。下一开放环 = 运行时联调验收（后端 8080 + MySQL/Redis 就绪）→ 逐页走查修正 → 替换/并存落地 → 更新本文件与可读版交接文档。
- `2026-10-16T12:05:00Z` — `opencode`(DeepSeek V4.1 Flash Free): **前端地址清单（供接手方直接核对）**——`GET /auth/me`、`POST /auth/login`、`POST /auth/change-password`、`POST /auth/logout`；`GET/POST /accounts`、`PUT /accounts/{id}`、`POST /accounts/{id}/reset-password`；`GET /users`、`GET /users/{id}`、`GET /users/{id}/credits`（默认 pageSize=10、max 50）、`POST /users/{id}/ban`、`POST /users/{id}/unban`、`POST /users/{id}/credit`（仅 super）；`GET /reports`、`GET /reports/{id}`、`POST /reports/{id}/handle`、`POST /reports/{id}/reject`；`GET /contents`（targetType 必填）、`POST /contents/{targetType}/{id}/take-down|restore|delete`；`GET/POST /categories`、`PUT/DELETE /categories/{id}`、`GET/POST /tags`、`PUT/DELETE /tags/{id}`、`GET/POST /sensitive-words`、`DELETE /sensitive-words/{id}`、`POST /sensitive-words/import`（后端默认 pageSize=50、max 200）；`GET/POST /banners`、`PUT/DELETE /banners/{id}`、`GET/POST /notices`、`PUT/DELETE /notices/{id}`、`POST /notices/{id}/publish|offline`、`GET /dashboard`；`GET /operation-logs`（adminId+action 筛选）。以上均相对 baseURL `/api/admin`。注意：`AdminCatalogController` 与 `AdminOpsController` 的类级 `@RequestMapping` 是 `/api/admin`，敏感词/分类/标签接口的前缀已在类级声明，前端调用无需再补 `/admin`。
- `2026-10-08T08:00:00Z` — `codex` set status to `accepted`: 已读取交接、规范与双前端结构；用户确认本轮主目标为 frontend-web 学生端设计。
- `2026-10-01T13:53:31Z` — `zcode`(GLM) set status to `ready`: 需求阶段完成，首次交接；下一开放环 = 数据库设计
- `2026-10-01T14:02:00Z` — `zcode`(GLM) set status to `accepted`: 接收方确认为 `to: either`、非 git 仓库、分支/提交字段不适用，工作区与记录一致
- `2026-10-01T14:02:30Z` — `zcode`(GLM) set status to `in_progress`: 开始数据库设计（23 张表字段级设计 + ER 图 + schema.sql）
- `2026-10-01T14:15:00Z` — `zcode`(GLM)（仍为 in_progress）: 设计完成，产出《数据库设计文档.md》与 `sql/schema.sql`。实机执行验证受阻：MySQL80 服务需管理员权限，控制台方式拉起 mysqld 成功但 root 密码未知（未继续穷举），已恢复服务原停止状态
- `2026-10-01T14:25:00Z` — `zcode`(GLM)（仍为 in_progress）: **实机验证通过**。用户提供 root 凭据后 `schema.sql` 一次执行成功（23 表/种子/FULLTEXT/事务冒烟/唯一约束实测），测试数据已清空，凭据未写入任何文件
- `2026-10-01T14:56:00Z` — `zcode`(GLM)（并行会话）set status to `ready`: **工程骨架阶段完成并全量验证**（后端 mvn package + 启动冒烟、双前端 npm build 全绿；骨架配置已与 schema.sql 对齐：库名 campus_secondhand、状态值软删、order_info、角色大小写口径）。数据库设计文档仍待用户审阅（T1~T12）。新 handoff_id=20261001-225500，下一开放环 = 2026.11 核心模块（实体层 + USR + GDS）
- `2026-10-01T15:20:00Z` — `zcode`(GLM)（设计会话，本握手保持 `ready`）: 补记——原需求会话在骨架进行期间产出《前端设计文档.md》v1.1（用户决策：不做高保真原型，但要"尽可能好看的页面"），已与双应用骨架**对齐**：双应用结构与端口、实际路由表（前台 15 路由 / 后台 10 路由）、守卫实现口径（web 仅 requireAuth，细粒度由后端返回码驱动）、命名以 `docs/命名规范.md` 为准。视觉 Token（清新校园绿 #00B578 + 价格橙 #FF6B35，集中 `styles/tokens.scss`）尚未落地——**已列为模块①前置任务**（骨架样式现为占位、主色仍 EP 默认蓝）。
- `2026-10-01T16:10:00Z` — `zcode`(GLM)（设计会话，本握手保持 `ready`）: **数据库设计经用户审阅修订为 v1.1 并完成实机重建验证**。9 条意见全采纳：T1 标签独立（新增 tag/goods_tag，分类管导航筛选、标签管推荐匹配）、T3 核心关系 41 条物理外键（多态/审计/跳转保持逻辑外键 + 定期孤儿扫描）、T4 软删必须记录 deleted_at/deleted_by_type/deleted_by/delete_reason 并定义删除/恢复/订单期间禁删/历史查询规则、T5 派生字段必须有事实表+唯一约束+事务内原子更新+定时重建（新增 goods_want 想要事实表；message.read_at）、T7 交换订单双方参与者模型（buyer/seller 仅兼容展示，buyer/seller_confirmed_at 双确认才完成）、T8 待处理应约/交换请求改生成列唯一约束 + 事务锁，接受时原子关帖并拒绝其余待处理、T9 浏览按用户/商品/行为/业务日期唯一索引、T11 订单号每日序列表事务生成 + 溢出扩位、T12 管理员凭据环境变量注入 + 首次登录强制改密 + 幂等初始化；T2/T6/T10 维持。表数 23→**27**，正文残留的"23 张表/23 个实体类"旧计数已同步修正，以此为准。schema.sql 实机重建验证：27 表/41 FK/种子分类 10；行为测试全过（FK 拦截 1452、待处理应约唯一 1062 且拒绝后可重申、VIEW 同日去重且 ORDER_DONE 可同日重复、标签关联、回滚干净）。实测修复两坑：①含 STORED 生成列的表 FK 动作必须 RESTRICT/RESTRICT（CASCADE 被 InnoDB 拒绝）；②order_no_seq 单语句 upsert 当天首次取号返回 0（无自增列），改两步式 INSERT IGNORE + UPDATE，实测 1→2→3。前端设计文档同步 v1.1（发布表单标签多选 0~5、GoodsCard/详情标签展示、SWAP 双确认时间线、后台路由路径修正），并收录用户追加的 §10 品质标准（Awwwards/Webby/FWA 获奖级自检指令）。实体层验收标准已更新为 27 个实体类。
- `2026-10-01T16:10:00Z` — `zcode`(GLM)（设计会话，本握手保持 `ready`）: **数据库设计经用户审阅修订为 v1.1 并完成实机重建验证**。9 条意见全采纳：T1 标签独立（新增 tag/goods_tag，分类管导航筛选、标签管推荐匹配）、T3 核心关系 41 条物理外键（多态/审计/跳转保持逻辑外键 + 定期孤儿扫描）、T4 软删必须记录 deleted_at/deleted_by_type/deleted_by/delete_reason 并定义删除/恢复/订单期间禁删/历史查询规则、T5 派生字段必须有事实表+唯一约束+事务内原子更新+定时重建（新增 goods_want 想要事实表；message.read_at）、T7 交换订单双方参与者模型（buyer/seller 仅兼容展示，双确认才完成）、T8 待处理应约/交换请求改生成列唯一约束+事务锁，接受时原子关帖并拒绝其余待处理、T9 浏览按用户/商品/行为/业务日期唯一索引、T11 订单号每日序列表事务生成+溢出扩位、T12 管理员凭据环境变量注入+首次登录强制改密+幂等初始化；T2/T6/T10 维持。表数 23→**27**，正文残留的"23 张表/23 个实体类"旧计数已同步修正，以此为准。schema.sql 实机重建验证：27 表/41 FK/种子分类 10；行为测试全过（FK 拦截 1452、待处理应约唯一 1062 且拒绝后可重申、VIEW 同日去重且 ORDER_DONE 可同日重复、标签关联、回滚干净）。实测修复两坑：①含 STORED 生成列的表 FK 动作必须 RESTRICT/RESTRICT（CASCADE 被 InnoDB 拒绝）；②order_no_seq 单语句 upsert 当天首次取号返回 0（无自增列），改两步式 INSERT IGNORE+UPDATE，实测 1→2→3。前端设计文档同步 v1.1（发布表单标签多选 0~5、GoodsCard/详情标签展示、SWAP 双确认时间线、后台路由路径修正），并收录用户追加的 §10 品质标准（Awwwards/Webby/FWA 获奖级自检指令）。实体层验收标准已更新为 27 个实体类。
- `2026-10-01T16:25:00Z` — `zcode`(GLM)（骨架会话）: **分支指令更新**——用户明确"本会话分支 = master，后续推送一律推 master"（覆盖此前 dev 工作分支约定）。已核验 backend/frontend-web（dev 与 master 无差异）并切换至 `master`、与 origin/master 同步；`docs/命名规范.md` §5 同步改为 master 口径。另：骨架阶段产出的文档已按数据库设计 v1.1 修正口径（27 表 / 41 条物理外键 / 软删记录 deleted_at 等元数据）。
- `2026-10-02T01:30:00Z` — `zcode`(GLM)（主代理会话）: **用户 review 完成，D1 两项重大问题已修复并实测通过（未提交，等 review 确认）**。① 新增 `POST /api/goods/{id}/on-sale`（仅本人、仅 OFF_SALE→ON_SALE；`off_sale_reason` 非空即管理员下架，卖家不可自行上架返回 40303，需 M6 管理员恢复）——OFF_SALE 死状态解除；前端详情页按钮修正：OFF_SALE→"重新上架"(on-sale)、DELETED(仅本人可见)→"恢复"(restore)，api/goods.js 增 onSaleGoods。② 敏感词种子库 95 词写入 schema.sql 并同步现有库（类目参照电商禁售清单；实测"枪支弹药"发布被拦 40001 并报命中词）。实测：off-sale→on-sale→ON_SALE ✓、管理员下架场景 40303 ✓、敏感词 40001 ✓、frontend build 绿。
- `2026-10-02T01:30:00Z` — **用户 review 的 D2 四项中等问题的处置建议（均"记录在案"，待用户逐项确认后再动）**：
  1. handoff 验收标准"27 个实体类"口径不符——实际已落地 12 个（M1:User/UserAuth/Admin + M2:Goods/GoodsImage/Category/Tag/GoodsTag/GoodsWant/Favorite/SensitiveWord/UserBehavior），其余 15 张表实体归 M3~M6 按需落地。建议改口径为"按模块递进落地，27 个为终态"，待确认后修改本文件验收标准。
  2. GoodsServiceImpl.resolveAuthNotCertified() 反射取枚举为并行期防冲突 hack，现 ErrorCode.AUTH_NOT_CERTIFIED 已存在可改直接引用；requireCertified 与拦截器重复查库（LoginUser.authStatus 已刷新）；creditScore 无消费者——信用受限(<60)禁发布策略未接入，建议 M3 信用分模块上线时统一接入。待确认。
  3. 个人中心"我的商品/收藏"Tab 仍占位（后端 API 已就绪）、getMyOverview 无调用——建议随 M2 收尾接线。待确认。
  4. 登录 IP 限流（PRD §9.2"5 次/分钟/IP"）未实现，现仅用户名维度锁定。待确认是否 M1 补或并入 M7 安全收尾。
  另注：库中现存 m12a~d/e2e 等测试用户与商品为并行会话验收数据，非垃圾数据，勿清理。
- `2026-10-02T14:15:00Z` — `zcode`(GLM)（主代理会话）: **M3（交易与信用）/ M4（即时消息）开发完成，全链路 E2E 验收通过（未提交，等用户 review）**。子代理两次派发均失败（验证码/超时），但 M3/M4 智能体在失败前已留下近乎完整产出，主代理按验收流程接管：编译 0 错、ErrorCode 按预定段位精确落地（ORDER 40402/40305/40906~40908/40009、CHAT 40403/40306/40010）、共享契约 NotificationService 被双方正确使用。实测：下单锁定+want_count+1（T5）+两步式取号单号格式 ✓；确认→SCHEDULED ✓；完成→COMPLETED+商品 SOLD+双方信用 102（credit_log 各 +2）✓；评价→重复 40908 ✓；受限（信用 50）下单 40302 ✓（LoginUser.creditScore 直读，顺带消化 D2-2 的重复查库问题）；超时扫描→CANCELLED/TIMEOUT+解锁+卖方 -2 ✓；聊天发消息/会话未读/已读置位/商品卡片/敏感词 40001/越权 40306 ✓；通知单条已读/全部已读/越权 40400 ✓；WS 握手 101/无 token 401 ✓（浏览器双开互发留联调）。CHT-04 图片消息 P2 按计划跳过。前端三页（订单两页+消息中心 957 行）+ api/store 全部实现，npm build 绿。测试数据已清理。**待用户 review 后提交**；提交建议 4 笔：feat(order)/feat(chat)/feat(web)/fix+chore 汇总。
- `2026-10-02T15:00:00Z` — `zcode`(GLM)（主代理会话）: **用户第二轮 review 修复完成（未提交）**。D1 两项：① OrderServiceImpl.detail() 的 Set.of() 改为 loadReviewedOrderIds(List.of(order),viewerId)，实测 hasReviewedByMe=true/canReview=false；② schema.sql 补 19 个二级分类种子（按用户在演示库补的数据照抄），全新建库可发布。D2 五项全修：③ GoodsDetail"聊一聊"（发 GOODS_CARD 建会话→跳消息中心）与"想要"（createOrder→跳订单详情，非在售状态 tooltip 说明）接线，PRD 用户旅程打通；④ vite.config.js 增加 '/ws' ws:true 代理；⑤ MainLayout 接入 msg store 双红点（铃铛/消息图标 + 30s 轮询启停）；⑥ cancel() 卖家通知文案"您已取消"→"买家已取消"，实测 B 收到"买家已取消订单 SH…，商品已重新上架"；⑦ 个人中心七 Tab 接线：后端新增 GET /api/goods/my（pageMine，复用 buildCards）+ GET /api/credits/my（新 CreditController），前端 ProfileView 重写（我的商品/收藏/买到/卖出/收到的评价/信用记录真实数据 + 我的举报 M7 占位）。D3 处置：残留 JSON 16 个已删（backend 根 8 + market 包 8）；其余三项记录在案——m2tool 三个 TestTokenMain M5 前统一删、copyBase 20 字段手工复制重构（建议 OrderDetailVO extends OrderListVO 或 MapStruct）放 M7、credit_log 幂等并发窗口加 (user_id,reason,ref_type,ref_id) uk 需改表待下次 schema 变更一并确认、GOODS_CARD 推送载荷 coverUrl null 微小 UX 记录。前端 build 绿、后端编译绿、修复项全部实测。**待 review 后提交：4 笔 feat + 本轮 fix 合并或单独 fix(goods)/fix(order)/feat(web)**。
- `2026-10-02T09:35:00Z` — `workbuddy`(DeepSeek)（主代理会话）set status to `ready`: **M3+M4 已提交并推送 GitHub（`env` 分支）**。提交切分：backend `f542c72` `fix(goods): 个人中心"我的商品"分页接口（D2-3）` + `91300ae` `feat(order,chat): M3 交易与信用 + M4 即时消息与站内通知`（51 文件 / +3443 行）；frontend-web `a0543a7` `feat(web): M3/M4 前台对接`（12 文件 / +2113 行）。提交前全量验证：`mvn -B -DskipTests package` BUILD SUCCESS、`npm run build` ✓ built（主 chunk 1.27MB 告警沿用既往记录，未处理）。顺手修掉一处格式回归：GoodsServiceImpl 的 `buildCards` Javadoc 与方法声明被并到同一行（已拆回两行）。两仓库工作区干净、与 `origin/env` 一致（backend 91300ae / frontend-web a0543a7）。**推送排障（已写入本文件 Current state 与项目内存）**：系统 credential.helper=helper-selector 在无 tty 环境挂起 → 必须 `-c credential.helper= -c credential.helper=manager`，并走代理 127.0.0.1:7890。下一开放环 = **M5 特色板块与推荐**（want/swap 前后端均未开工，6 个前端页面仍为占位）。
- `2026-10-02T11:10:00Z` — `workbuddy`（续接会话）: **M5 复核冒烟通过，待用户 review 后提交**。本日另一并行会话已完成 M5 开发（API 验收 87/87，见 `.workbuddy/memory/2026-10-02.md`）但未提交；本会话独立复核：`mvn package` ✓、`npm build` ✓；API 冒烟（18080 新 jar 连 3307 隔离临时库）——求购发帖/自约 40910/应约/重复应约 40915/接受生成 PURCHASE 订单（buyer=求购者、seller=应约者）/SWAP 发帖→发起→同意生成 SWAP 订单/recommend home+hot+similar+dev rebuild 全部 ✓（个性化正确排除自己发布的商品；订单列表 50000 为缺 `role` 参数请求姿势问题，非 bug）。冒烟数据已按外键顺序清理，既有验收数据未动。**环境收尾**：遗留的 18080/18082 旧冒烟 java 进程已全部关闭；8080+5173 演示环境保留存活（账号 m5a/m5b/m5c，密码 `Passw0rd!`）。**当前 git 状态：backend（5 改 + 31 新增）与 frontend-web（9 改 + 3 新增）在 `env` 工作区未提交**，提交建议：backend `feat(want,swap,rec)` 一笔 + frontend-web `feat(web): M5 前台对接` 一笔。注意：3307 为并行会话的隔离临时 MySQL（datadir 在系统临时目录），用户真实库 3306 root 密码未知未联调。
- `2026-10-02T13:55:00Z` — `workbuddy`（续接会话，测试+M6 开工）: **M1~M5 单元测试全部完成并推送，M6 C5 本地完成待推**。① 用户 settled 六项决策（先推 M5→串行先测后 M6→Mockito+Testcontainers 混合→全覆盖分层深测→M6 后端先行→mvn test 进验收门禁）。② M5 两笔提交已推（backend `39f32ee`/frontend-web `510042f`）。③ 测试基建 + 14 个 ServiceImpl 测试类 + AuthInterceptor/AdminInitializer 测试 + 5 个集成类，**169 测试全绿**（10 跳过=无 Docker 优雅降级，`disabledWithoutDocker=true`）；测试抓到并修复 UploadServiceImpl 缩略图解码 bug（`b1fef26`）。测试提交 C1~C4 已推（`8ce3936`）。④ M6 C5（后台登录/账号管理/操作日志 AOP/强制改密门禁 40310/ErrorCode ADM+RPT 段/starter-aop）本地提交 `f64c80c` 待推。⑤ **⚠️ 并行会话冲突**：另一会话同工作区开发中（删 m2tool、GoodsMapper.selectUserAuthStatus→新 UserAccessGuard、新增 Report*/OrphanScan* M7 域半成品）→ 工作区 testCompile 暂失败；本会话按用户指示**暂停 M6（C6 用户管理起）**，待另一会话完成后接手适配（重点：UserAccessGuard 认证口径统一 + GoodsServiceImplTest 需适配）。多会话并存时 git add 必须精确清单（教训已入记忆）。⑥ C5 推送受阻（代理换 12729 对 github 502、直连挂起），待用户修复网络后重试。
- `2026-10-02T10:20:00Z` — `workbuddy`(DeepSeek)（主代理会话）set status to `ready`: **M5 特色板块与推荐开发完成，API 验收 87/87 全过（未提交，等 review）**。
  交付：backend 新增 4 实体（WantPost/Offer/SwapPost/SwapRequest）+ 4 Mapper + 3 Controller（WantPost/SwapPost/Recommend）+ 7 DTO + 6 VO + 3 Service 接口/实现 + `task/RecommendTask`；frontend-web 3 新 api（want/swap/recommend）+ 6 页面由占位改为完整实现（求购广场/详情/发布、交换广场/详情/发布）+ 首页猜你喜欢接 `/recommend/home`、热度榜接 `/recommend/hot`、详情页相似推荐改由后端随详情返回。
  跨模块接线（M5 需要，非越界）：① `GoodsService.cardsByIds` 新增（推荐位按序装配卡片）；② `FavoriteServiceImpl` 补 `user_behavior(FAVORITE)`、`OrderServiceImpl.finishOrderRewards` 补 `ORDER_DONE`（PRD §6.6 推荐的"收藏/成交"输入此前无人写入）；③ `OrderServiceImpl.loadSourceTitles` 回溯 PURCHASE/SWAP 订单标题（`order_info` 无标题快照列，PRD §4.3 来源可追溯）；④ ErrorCode 追加 REQ/SWP 段（40011/40012/40307/40308/40404~40407/40909~40916）。
  验收口径（脚本 `.m5mysql/m5_e2e.sh` 位处系统临时目录，非仓库内）：87 项断言含 REQ-01~04 全流程（发布→应约→接受原子关帖+拒绝其余→生成 PURCHASE 订单）、SWP-01~03 全流程（发布/发起/同意→SWAP 订单即 SCHEDULED→双确认 COMPLETED）、REC-01~03（冷启动热门兜底/个性化命中他人商品/相似推荐排除自身/heat_score 重算/Redis 缓存命中）、教材与分类检索、权限与错误码、通知与信用流水、订单号格式与孤儿检查。
  验收环境（**未触碰用户真实库**）：独立临时 MySQL 8.0.44 实例（端口 3307 / datadir 在系统临时目录，root 无密码，`sql/schema.sql` 全量建库），后端跑在 8080 与 18081 上验证过；用户自己的 MySQL（3306）因 root 密码未掌握且原为停止状态，本轮未使用亦未改动。
  **演示环境仍在运行**（供用户 review）：后端 http://localhost:8080（M5 代码 + 临时库）、前台 http://localhost:5173；测试账号 m5a/m5b/m5c（均已校园认证）、m5d（未认证，用于验证拦截），密码 `Passw0rd!`；演示库含 2 商品 / 2 求购帖（1 OPEN 1 DEALT）/ 2 交换帖（1 DEALT 1 OPEN）/ 2 订单（PURCHASE、SWAP 各 1 已完成）。**kill 掉临时 mysqld 与两个后端进程后临时环境即失效**。
  下一开放环 = **M6 管理后台**（ADM-01~09；用户 review 后先提交 M5：建议 backend `feat(want,swap,recommend)` 一笔 + frontend-web `feat(web): M5 前后端对接` 一笔，或按求购/交换/推荐拆三笔）。
- `2026-10-02T13:26:00Z` — `workbuddy`（GLM-5.3-Flash，WorkBuddy 会话）set status to `accepted`: 接收交接并实测核对工作区，与文档记录的差异如下：① **M5 已由后续会话提交并推送 `origin/env`**——backend 最新 `8ce3936`（其前有 `39f32ee feat(want,swap,rec)` M5 主提交、`b1fef26 fix(upload)` 缩略图降级、四笔 test 提交：Testcontainers MySQL8 测试基建 + M1~M5 服务层单测/集成兜底），frontend-web 最新 `510042f`；两仓库 `origin/env..env` 均为空，即本地与远端同步、无未推送提交。② **工作区现有 M6 管理后台未提交 WIP**：backend 4 改（ErrorCode/AuthInterceptor/LoginUser/AuthInterceptorTest）+ 24 新增（AdminAuth/AdminAccount/OperationLog/Report 四 Controller、Report 与 OperationLog 实体+Mapper+Service、OperationLogAspect 注解切面、OrphanScanService/Task 孤儿扫描、IpUtils 登录 IP 限流、AdminLoginVO/AdminVO/OrphanScanVO/ReportVO）；frontend-web 7 改（MainLayout/ProfileView/GoodsDetail/SwapDetail/WantDetail/user store）+ 2 新增（api/report.js、ReportDialog.vue 举报入口接线）。③ frontmatter branch 由 master 修正为 env（遵用户 2026-10-02 指令口径），commit 字段更新为实测值。接收方已阅 Outcome/验收标准/守则，上下文获取完成；**下一开放环 = M6 管理后台收尾**（补齐 ADM 剩余模块 → API 验收 → 用户 review 后提交）。
- `2026-10-02T16:40:00Z` — `workbuddy`（GLM-5.3-Flash，WorkBuddy 会话）: **「模块8」= 模块清单第 8 行 M7 收尾与联调的剩余缺口交付完成（未提交，与其他 agent 的 M6/M7 WIP 同工作区，未触碰其文件）**。用户按清单行数派工：行6=M5、行7=M6 已给并行 agent，本会话做行 8。CRD-02 受限发布拦截与反射 hack 清理经核实已由并行 agent 以 `security/UserAccessGuard` 完成（含单测），本会话实测复验并交付其余缺口：
  ① **登录 IP 限流接线**（PRD §7/§9.2，原 IpUtils 只用于 admin 侧）：`AuthService.login(dto, clientIp)` 签名扩展 + `AuthController` 传 `IpUtils.clientIp(request)` + Redis INCR 计数（key `campus:market:auth:loginip:<ip>`，`AuthProperties.loginIpLimit=5/loginIpWindowSeconds=60` 可配），超限 42900；无论成败均计数防撞库。
  ② **前后端契约断裂修复（真实回归）**：前端 `api/goods.js` 调 `GET /goods/my`（个人中心"我的商品"Tab + SwapDetailView 关联商品），但 `GoodsController` 从未提交过该端点（f542c72 只交了 Service 层 pageMine）→ 实际运行 404 被 `/{id}` 吞掉变 50000。已补 `@GetMapping("/my")`（pageNum/pageSize 对齐前端参数）。
  ③ **前端受限/封禁全局提示**（M7 清单前端项）：`request.js` 响应拦截器处理 40301（ElMessageBox 弹窗展示封禁原因，去重防刷屏，不强制登出——封禁用户可查原因与期限）与 40302（warning 引导"完成订单恢复信用至 60 自动解除"）。
  ④ **每日 MySQL 逻辑备份脚本**（PRD §7，全项目此前缺失）：新增 `sql/backup.sh`（mysqldump --single-transaction 全对象 + gzip + 14 天保留 + 凭据走环境变量/~/.my.cnf 不入库不入 Git，含 cron 与 Windows 任务计划用法）。
  ⑤ **@Scheduled 清单复核**：4 任务齐备且 `SchedulingConfig @EnableScheduling` 已启用——OrderTimeoutTask(60s)/RecommendTask(1h)/ChatServiceImpl#rebuildDerivedFields(每日 03:30)/OrphanScanTask(每日 03:30)。
  验收：单测 `AuthServiceImplTest`(15，新增 IP 限流 2 例)+`GoodsServiceImplTest`(13，含受限发布例) 28/28 绿；`npm run build` 绿；E2E 冒烟（18085 新代码实例 + 3307 隔离临时库）——受限用户发布 40302 ✓、封禁用户业务接口 40301 且白名单 /users/me 放行（可查 banReason/bannedUntil）✓、/goods/my 返回真实数据 ✓、同 IP 第 6 次登录 42900 且超限后正确密码亦拒 ✓；冒烟数据（r7a/r7b/Redis key）已清理。
  **环境与协作注意**：`mvn package` 当前 BUILD FAILURE **纯因 8080 演示进程（PID 24816）锁定 target/*.jar 致 repackage rename 失败**，compile/test 全绿，8080 停后重跑即绿；并行 agent 编辑中曾两次造成瞬时编译错误（AdminContentServiceImplTest 缺 import、AdminReportServiceImpl 缺 import），前者已顺手补一行 import 解锁构建，后者对方自行修复；`STATUS_BANNED=1`（冒烟造数时误写 2 踩坑）。遗留记录：CHT-04 图片消息 P2、举报 USER 目标入口（ReportDialog 仅接了商品/求购/交换三处，动详情页会撞并行 WIP，留给 M7 对应 agent）、互动/下单接口限流（PRD §7 泛要求，仅登录已做）、HttpMessageNotReadableException 返回 50000 而非 400（观察项）。
- `2026-10-02T15:56:00Z` — `workbuddy`(DeepSeek)（主代理会话）: **M7 收尾与联调完成，API 验收 52/52 + 单测 191 项全绿（未提交，等 review）**。
  **归属更正（重要）**：上一条（13:26Z）把 `ReportController/ReportService`、`OrphanScanService/Task`、`frontend-web` 的 `ReportDialog.vue/api/report.js/三处详情页举报入口/个人中心"我的举报"Tab` 记为"M6 WIP"——实际这些文件是**本轮 M7 的交付**（用户明确指示"M6 有别的 agent 在做，你只做 M7"；M6 已提交部分为 `f64c80c feat(admin-auth)`）。请勿把 M7 文件误并入 M6 提交。
  **派发方式**：4 个子 agent（M7-A 后端举报+孤儿扫描 / M7-C 前端举报与受限提示 / M7-D 需求覆盖核对表+演示动线 三者并行；M7-B 全局拦截复查+收尾重构在 A 完成后串行，避免 Maven 与后端文件并发冲突），主代理统一做契约冻结、构建、E2E 与文档校正。
  **M7 交付**：举报（RPT-01/02，四类目标 + 截图证据 + 同目标去重）、孤儿数据扫描（T3，5 类逻辑外键，每日 03:30 + dev 触发，只统计不删数据）、前台举报弹窗与三处详情页入口、个人中心"我的举报"Tab、受限/封禁全局提示条、需求覆盖核对表（`docs/M7-需求覆盖核对表.md`：53 条实际需求，✅43/⚠️1/⛔9，ADM 段归 M6 不计完成度）、演示动线（`docs/演示动线.md`）。
  **收尾修复 4 项**（均由子 agent 定位、主代理复核并 E2E 验证）：① 受限用户"发布商品"未拦截（CRD-02 唯一缺口）→ `GoodsServiceImpl.publish/update` 走 `UserAccessGuard.requireInteractive`；② 封禁强拦截原耦合在 `UserServiceImpl.applyFreshState`，封禁用户连 `GET /users/me` 都取不到（PRD §3.1 要求可查看封禁原因/期限，前端横幅刷新后丢失）→ 拦截上移 `AuthInterceptor` + `BANNED_USER_ALLOWED_PATHS` 白名单，`UserDetailVO` 补 `status/banReason/bannedUntil`，前端 store 据 `status===1` 映射 `banned`、封禁期间停止轮询；③ 抽 `security/UserAccessGuard` 消除 4 处重复校验，删掉 `resolveAuthNotCertified()` 反射 hack 与重复查库；④ 删除 `m2tool/`（3 个绕过登录的取号工具）+ `OrderTimeoutTask` 补 try/catch。另：`OrderDetailVO` 已由前序会话改为 `extends OrderListVO`，本轮改为泛型 `buildListVO(T,…)` 复用，删除 `copyBase`。
  **验收证据**：`mvn test` **191 项 0 失败**（新增 3 项封禁白名单测试；10 项 `integration/` 因无 Docker 优雅跳过）；`mvn -DskipTests package` SUCCESS；`vite build` ✓（`ReportDialog` chunk 正常产出；注意 `npm run build` 在清空 `dist/` 时会触发本环境 safe-delete 保护，改用 `npx vite build --outDir <临时目录> --emptyOutDir`）；M7 API E2E 52 项断言全过（脚本在系统临时目录 `m5mysql/m7_e2e.sh`，隔离库 3307 + 后端 8080）。
  **演示环境**：后端 8080（最新 jar + 临时库 3307）+ 前台 5173；账号 m5a/m5b/m5c（已认证）、m5d（未认证），密码 `Passw0rd!`；库内含 3 商品 / 3 求购帖 / 2 交换帖 / 2 订单 / 7 条举报工单（全部待处理，处置侧待 M6）。**注意：3307 与 8080 的后台进程在本机会被回收（实测闲置约 2 小时后 java/mysqld 均消失），需用时按 README/本条记录重启**。
  下一开放环 = **M6 管理后台收尾**（举报处置闭环 ADM-04 需复用 `ReportService.markHandled`，勿新建同类）+ 用户 review 后提交 M7（建议 backend `feat(report,orphan): M7 举报与孤儿扫描` + 收尾修复一笔，frontend-web `feat(web): 举报入口与受限提示` 一笔）。
- `2026-10-03T00:55:00Z` — `workbuddy`（续接会话，M6 后端）: **M6 后端九项能力全部完成并推送（`079f1a8`），mvn test 250 全绿**。C5 后台登录/账号管理/操作日志 AOP（`f64c80c`）→ C6 用户管理（`7f06c7c`）→ C7 分类标签+敏感词库（`bc1da50`）→ C8 内容巡查处置（`5b1225e`）→ C9 举报工单组合处置（`34f6503`）→ C10 轮播/公告/大屏+前台读取（`079f1a8`）；另代提交 DeepSeek 会话的 M5 收尾两笔（`adc0f8d` 封禁白名单+UserAccessGuard、`fcc192c` 举报前台+孤儿扫描）。**ErrorCode 新段**：ADM 40013/40014/40309/40310/40408/40917、RPT 40409/40410/40918、目录 40919/40920/40411。**权限矩阵**：账号/日志=super；内容/词库/举报=super+auditor；用户/目录/轮播=super+operator；大屏=三角色。**多会话并存纪律**（重要，已入项目记忆）：git add 精确清单；动手前 test-compile 探测；静默>2h 才代提交并行成果。**接续点**：frontend-admin 10 页前端（/api/admin/** 接口全就绪）；5 个集成测试类等 Docker Desktop；M7 公示；真实库 3306 联调。GLM 会话的 M7 收尾交付（IP 限流等）仍在工作区未提交，等其自行提交或用户 review。
- `2026-10-03T02:10:00Z` — `zcode`(GLM)（验收会话）: **M5/M6/M7 代码验收+功能验收完成**。实测：单测 250 项全绿（10 跳过无 Docker）、M5 E2E 87/87、M7 E2E 52/52（均在 3307 隔离临时库 + 8080 全新 jar 重跑）、M6 两轮冒烟（52 项断言，40+ 过）。**确认缺陷 13 项（P0×1、P1×8、P2×4）+ 改进建议 12 项**，最重的：① frontend-admin 登录响应取 `data.admin` 但后端为扁平 VO → 后台登录后实际不可用；② AdminReportServiceImpl.resolveTargetOwner 缺 USER 分支 → 举报"用户"的工单永远 40410 无法处置（E2E 实证）；③ 后台用户列表/详情直接返回 User 实体，BCrypt password 哈希随 JSON 泄露（E2E 实证）；④ AdminUserController 类级 @RequireRole super+operator 与 §4.2 矩阵错配（operator 可调信用分/封禁，auditor 反而无权）；⑤ 下架/删除内容未通知发布者（E2E 实证）。**修复建议与完整清单见验收报告（已交用户）**。环境：8080 现为验收专用新实例（含未提交 WIP 代码）连 3307 临时库，管理员 admin（密码临时设定不入档）；m5_e2e_8080.sh/m7_e2e.sh 已补"文件体 POST"补丁（本机 Git Bash 对原生 curl 有 UTF-8→GBK 参数转码，内联中文 JSON 必挂，新脚本务必用 --data-binary @file 模式）；验收产生的测试数据留在临时库可随时重置。M5/M7 功能验收通过；M6 后端功能大部分通过（除上述缺陷），**M6 后台前端 10 页仍占位无法验收**。建议：先修 P0/P1 再提交 M7 前端 9 文件，M6 收尾时一并处理。
- `2026-10-03T07:00:00Z` — `zcode`(GLM)（修复会话）: **验收缺陷修复完成并已本地提交（push 待网络恢复）**。按用户建议顺序执行——P0：frontend-admin/src/stores/admin.js 适配扁平 AdminLoginVO（原取 data.admin 恒 null，后台登录即废；frontend-admin 无 git 仓库，修复留在工作区）。P1×4（后端，实测通过）：① AdminReportServiceImpl.resolveTargetOwner 补 USER 分支（AdminContentVO 增 TYPE_USER），USER 工单处置实测 200；②③ AdminUserController 类级改 {super,auditor} + 信用分端点单独 @RequireRole("super")，实测 operator/auditor 调分 403、super 过 RBAC、operator 访问用户列表 403；④ AdminUserVO 替代 User 实体出参，响应实测无 password 字段。P1 第 5 项（下架/删除通知发布者）+ P2×4（IpUtils XFF 伪造/限流 increment+set 计数/敏感词大小写归一/markHandled 前置条件/restore 校验管理员下架）+ P3 改进建议 12 项 → **M6 收尾清单**（下一会话执行）。D3 顺带：测试残留 JSON 16 个已删（backend 根 8 + market 包 8）。本地提交：backend `ca70fab` feat(security) M7 收尾 + `0d77b65` fix(admin) 验收 P1；frontend-web `01775a3` feat(report) M7 前端 9 文件（含个人中心七 Tab/双红点/详情页下单聊天接线/vite /ws 代理）。**push 未成功**：github 直连与 127.0.0.1:7890 代理均不可达（网络环境），恢复后 `git push origin env` 两仓库即可。验证环境已清理（18082 实例停止；8080 演示实例保留未动）。注意：3306 mysqld 本机会被回收，重启命令见既往记录。
- `2026-10-04T20:55:00Z` — `zcode`(GLM)（验收修复会话）: **验收报告中的缺陷已全部修复并 E2E 复验通过（未提交，等 review）**。分工：并行会话修了 P0（admin.js 扁平映射）、USER 举报分支（0d77b65）、AdminUserVO 脱敏、RBAC 矩阵对齐（含 dashboard）、下架/恢复/软删三处通知（notifyOwner）、登录 IP 限流落地（ca70fab）；本会话修了 **OperationLog 三角色只读、SWAP 订单锁定关联商品（createTransactionOrder 条件扩展，完成/取消/超时路径自动正确）、推荐缓存读路径 try/catch 降级、frontend-admin 守卫重定向环（async + fetchMe）、frontend-web fetchProfile 显式映射 userId**，另按验收发现补了 GlobalExceptionHandler 两个映射：HttpMessageNotReadable→40000、NoResourceFoundException→40400（原落 50000）。复验证据：M5 E2E 86/87（唯一失败为脚本 GBK 编码伪缺陷）、M7 52/52、SWAP 接受后商品实时 IN_TRANSACTION、completed SWAP 商品 SOLD、下架通知实测送达、auditor operation-logs 200、operator 封禁 40300、auditor 调分 40300、40400/40000 映射生效、双前端 vite build 绿。**已知红**：AdminServiceImplTest/AuthServiceImplTest/AdminContentServiceImplTest 共 7 项失败——均为并行会话在途改动（登录防护/通知注入）后测试未同步，归属其收尾。**注意**：backend 根目录与 src/main/java/com/campus/market/ 下有并行会话产生的临时 json 文件（cd2.json/al.json/h1.json 等约 14 个），提交前必须清理，勿 git add .。8080 现跑最新 jar（连 3307 新临时库，admin/Admin@12345 首登强制改密为 Admin@67890），3307 为全新 data-review 实例。
- `2026-10-03T09:30:00Z` — `zcode`(GLM)（修复会话）: **M6-F 后台前端 10 页完成并验收通过（frontend-admin 无 git 仓库，产出留在工作区）**。子智能体交付 + 主代理独立验收（build 绿、强改密门禁/举报证据防御解析/调分 super 判断/operation-logs 对接抽查全过，10 页共 2887 行）。子智能体报告的 6 处接口与文档不一致均已防御式对齐（改密端点 /auth/change-password、举报返回实体+images JSON 字符串、日志 /operation-logs 仅 adminId+action 筛选、账号启停走 PUT status、敏感词全量客户端分页）。**后端待补清单（下轮）**：① /api/uploads 放开 ADMIN 主体或加管理端上传（轮播图上传被拦）；② admin 端用户信用流水查询接口（详情抽屉 timeline）；③ DashboardVO 补认证用户数/举报处理时效/30 天活跃字段；④ admin reports 补 targetTitle 快照与 targetType 筛选参数。**至此全部 13 项确认缺陷修复完毕**（P0+P1×8 已提交推送 0d77b65/ca70fab/01775a3；P2×5 本地提交 59d7b08 已推送；P3 已启用切面/后台登录防护/IP 补齐，其余记录在案）。
- `2026-10-03T10:30:00Z` — `zcode`(GLM)（修复会话）: **第三轮 review 四项处置完成**。① 测试残留 JSON 23 个全删（backend 根 4 + market 包 8 + service/impl 7 + wl/cd2 等），.gitignore 加防回归规则（根 /*.json + /src/main/java/**/*.json + ct.txt），提交 `edb0ba8` 已推 origin/env；根因与防复发规范已入项目记忆（E2E payload 一律写系统临时目录）。② schema.sql 二级分类种子**实际已在上轮补入**（本文件 626 行起 19 条）——用户"仍缺"判断源于旧副本；本次用 sed 改库名在临时库 verify_seed 全量重建实测：29 分类（10 一级+19 二级）/95 敏感词/二级分类就位，证明"全新建库可发布"，临时库已删。③ 轮播确认：OperationConfigView 有 el-switch 上线/下线开关 + "新建后默认下线，用卡片上的开关上线"提示文案，无 bug。④ 举报表防重复（先查后插既定口径）确认接受；与 credit_log 幂等 uk 一并列入下次 schema 变更候选。注意：工作区有并行会话 WIP（GlobalExceptionHandler/OperationLogController/OrderServiceImpl/RecommendServiceImpl 四个 M 文件，疑似 HttpMessageNotReadable 400 化观察项的处理），本轮未触碰。
- `2026-10-05T15:30:00Z` — `zcode`(GLM)（修复会话）: **M6 收尾第二轮完成（后端已提交推送，前端 admin 配套由 M6-F 子智能体续跑中）**。按计划执行：A1~A4 四接口（uploads 放行 ADMIN/admin credits 分页/Dashboard 补 authUserTotal+reportAvgHandleHours+dauTrend30（UserBehaviorMapper 新增 DAU 聚合）/reports 改 PageResult<ReportVO> 含 targetTitle 快照与 targetType 筛选）+ 敏感词分页化——E2E 全过（18083 实例：admin 上传 200/credits 流水/dashboard 三字段/reports VO 快照/分页 total=95）。B 后端 P3 六项（page 形参错位修正、计数口径统一为待处理、魔法数字常量化、锁序注释对齐、推荐冷启动与补齐排除自己商品）。B 前端 P3 四项（utils/confirm.js 封装 + 19 处调用改造、want/swap 详情 watch 路由、GoodsDetail 竞态守卫、删 7 个死代码导出）。提交：backend `fd9c18b`+`52a42f3`、frontend-web `66edff8` 均已推 origin/env。**遗留观察**：E2E 中发现并行会话 WIP 4 文件（GlobalExceptionHandler 等）仍工作区未提交，未触碰。
- `2026-10-05T16:00:00Z` — `zcode`(GLM)（修复会话）: **M6-F 配套前端改造完成并验收通过**（SendMessage 续用原子智能体上下文）。9 文件改动（api 3 改 + 视图 5 页 + 2 新组件）：轮播 el-upload 直传（完整路径 /api/uploads + token 头）、用户抽屉信用 timeline（五 reason 中文映射 + 加减分着色 + 加载更多）、大屏加认证用户指标卡与 DAU30 面积图、举报工作台 targetType 筛选 + targetTitle 快照列 + 举报人列、敏感词切服务端分页（含数组/分页双形态兼容——子智能体与我的后端分页改动并行开发，兼容层正好闭环）。frontend-admin build 绿、五处抽查全过（无 git 仓库，产出留工作区）。**本轮"4 接口 + 9 项 P3 + 配套前端"全部闭环**；待用户最终 review。已知剩余：并行会话 4 个 WIP 文件、Testcontainers 5 类等 Docker、举报 USER 前台入口、互动限流。
- `2026-10-05T16:40:00Z` — `zcode`(GLM)（修复会话）: **新落地页第一版交付**（landing/index.html，单文件自包含，规格书逐条实现：1487×1058 u/h 单位系统、CloudFront 视频满屏、双渐隐遮罩、品牌 S 标志 SVG、四 logoipsum 印记、confirm 级入场动效为纯 CSS 零依赖、竖屏 burger+遮罩菜单）。浏览器实测：桌面 1487×1058 与竖屏 390×844 双分辨率截图核验，修复手机端 span inline 标题粘连缺陷。动效选型：纯 CSS（无动效库）。预览：http://127.0.0.1:8090/（临时静态服务器）或直接双击 index.html。与毕设前端集成待用户定方向。
- `2026-10-05T17:10:00Z` — `zcode`(GLM)（修复会话）: **落地页校园变体交付**（landing/campus.html + assets/campus-plaza.png，用户提供的学生视角校园广场插画）。同骨架不同色系：深绿墨舞台（--stage #060d09）替代纯黑、左列绿色 scrim 保白字可读、底部渐隐至深绿墨、Ken Burns 缓慢漂移替代视频（reduced-motion 跳过）、白 pill 加轻投影保亮背景轮廓。浏览器实测：桌面 1487×1058 全页 + 竖屏 390×844 均验收通过（文字对比度/构图/双 logo 网格/burger 均正常）。**经验**：IAB 截图在 setViewportSize 后可能取到合成器旧帧（表现为元素缺失），等待 2s 重试即恢复——是截图伪影非页面 bug。两版并存：index.html（暗黑视频版）/ campus.html（阳光校园图版）。
- `2026-10-16T12:00:00Z` — `OpenCode`(DeepSeek V4.1 Flash Free): 交接 `frontend-admin-react` React 重构（M0~M7 代码完成 + 静态验证通过），**运行时联调验收未做**，status=`ready`，`to: either`。产出 `docs/frontend-admin-react-交接文档.md`（接口对照表 §4 已逐条比对后端源码）+ 12 条验收清单。
- `2026-10-16T15:30:00Z` — `DSH`(DeepSeek)（联调验收会话）: **接收并完成 frontend-admin-react 的运行时联调验收，12 条清单全绿，status → `complete`**。
  **环境**（与用户真实库隔离）：隔离 MySQL 3307（`mysqld --no-defaults --datadir=<临时目录> --port=3307 --mysqlx=0`，后台任务方式）+ `sql/schema.sql` 全量建库 + 联调种子（4 用户/4 商品/2 求购/2 交换/5 举报/12 信用流水/1 轮播/2 公告）；后端 `java -jar target\campus-market-0.1.0-SNAPSHOT.jar --spring.datasource.url=…:3307…`，`ADMIN_USERNAME/ADMIN_PASSWORD` 触发 AdminInitializer（首登强制改密）；前端 `npm run dev` 5174。**踩坑**：① Chrome 在受限沙箱内无法启动（`mojo platform_channel Check failed: 拒绝访问` = 命名管道被拦），需 danger-full-access 拉起 `chrome.exe --headless=new --remote-debugging-port=9222`，再用 Playwright `chromium.connectOverCDP` 驱动（agent-browser CLI 亦可，但需 `AGENT_BROWSER_SOCKET_DIR` 指向可写目录）；② 沙箱内 `vite build` 报 `spawn EPERM`（esbuild 管道），非受限模式复跑即绿；③ `$env:TEMP` 会随沙箱策略切换，脚本一律用绝对路径；④ 占位符/`innerText` 里的空格会让 Playwright `hasText` 匹配失败（用 `textContent` 口径的连写字符串）；⑤ 登录接口有 IP 限流（5 次/分钟，成败均计数），批量登录脚本要限速。
  **实测修正 8 项前端缺陷**（只改前端）：管理员启停状态反转、用户封禁状态反转、`authStatus` 映射错误、分类/标签启停反转（以上 4 项为高严重度，均已与旧 Vue 版核对确认旧版正确）、已下线公告仍显示「发布」、敏感词导入计数用前端提交数、大屏本周趋势按固定 7 桶取数 + 栅格各占 1/5 空出 3/5、`mustChangePassword` 会话刷新后可绕过门禁。
  **实测结论**：`tsc --noEmit` ✓、`vite build` ✓（5.95s）、10 路由回归 **0 console error / 0 page error**、三角色 RBAC 菜单与越权重定向（含 toast）✓、强改密门禁（Esc 不可关 + 校验 + 改密后放行 + 刷新回落）✓、内容三门禁 ✓、举报证据图（真实加载 naturalWidth=900）与 USER 目标禁用「下架内容」+ 组合处置/驳回 ✓、封禁/解封/信用调整（132→137 且流水 +5）✓、轮播上传 `/api/uploads` 带 Bearer 返回 url 可预览 + 公告 草稿→发布→下线 ✓、敏感词 95/97 条分页筛选增删导入去重 ✓、管理员新建/启停/重置密码 ✓。**接口字段口径全部与 `src/api/types.ts` 一致**（`AdminLoginVO` 扁平 + role 小写 + mustChangePassword 布尔；`AdminVO` 的 id/mustChangePassword(0/1)；`CreditLog.scoreChange`；`ReportVO.images` 为 JSON 数组；时间 `yyyy-MM-dd HH:mm:ss`；`Result` + HTTP 恒 200）。
  **记录未修（后端侧，受"不改后端"约束）**：`AdminReportServiceImpl.buildVOs` 用单一 `Map<Long,String>`（仅 id 为键）合并 goods/want/swap/user 标题 → 举报列表 `targetTitle` 跨类型串号（GOODS#1 显示成 want_post#1 标题；详情接口单条解析正确），Vue 旧版同样存在。另：`creditDistribution` 中文键硬编码且 `<=59` 不含 NULL 分。
  证据：`gui-test-screenshots/admin-react/01~50-*.png`；脚本 `verify_api.mjs`/`verify_operator.mjs`/`pw_driver.cjs` + `act1~20/rep1~20`（临时目录）。**唯一剩余开放环 = 旧 `frontend-admin` 替换/并存决策（待用户）**，`docs/frontend-admin-react-交接文档.md` 已同步更新。
- `2026-10-16T16:00:00Z` — `DSH`(DeepSeek)（替换落地会话）: **用户决定「替换」，已落地并复验通过，本任务收口**。动作：① 停掉 5174 dev server 释放句柄；② 旧 Vue 版 `frontend-admin/` → **`frontend-admin-vue-archive/`**（原样归档，含 `node_modules`，可 `npm run dev` 回滚；新增 `README-ARCHIVE.md` 说明归档原因与回滚命令）；③ **React 版 `frontend-admin-react/` → `frontend-admin/`** 接管规范目录名；④ 清理 `node_modules/.vite`（含旧绝对路径的预构建缓存）；⑤ 从新路径重启 dev server 并复验：`/login` 200、`/api/health` 经 vite 代理返回 `{code:0}`、浏览器实测 登录→`/dashboard`→刷新仍在 `/dashboard`→`/content` 4 行→`/admin` 3 账号且启停文案正确、**0 console error**（截图 `51-after-replace-login.png` / `52-after-replace-admin.png`）。
  **踩坑（重要）**：`Rename-Item` 改 React 版目录名时报"访问被拒绝"（内部文件可写、子目录可改名、ACL 与已成功改名的旧目录**完全一致**，排除权限问题 → 判定为目录被某进程持有句柄）；**改用 `Move-Item` 直接成功**。前一轮该情形已用 `Get-Acl` 逐项比对排除 ACL 成因，未按 ACL 修复处理（未改任何权限项）。
  同步文档：根 `README.md`（目录树 + 快速启动 + 回滚命令）、`模块清单.md`、`docs/frontend-admin-react-交接文档.md`（§7 第 12 项、§9 决策表、§10 快速参考、§11.1 命令、新增 §12 替换落地记录）、`docs/演示动线.md`（§0.5 与 §10 过期表述加状态更新）、`docs/前端shadcn迁移方案.md`（"frontend-admin 暂不迁移"决策标注已推翻）、`frontend-admin/README.md`、新增 `frontend-admin-vue-archive/README-ARCHIVE.md`。保持原样的历史记录：`docs/M7-需求覆盖核对表.md`、`docs/命名规范.md`、`.workbuddy/**`。**无剩余开放环**（仅余非阻塞的用户可选项，见 ★ 区块 Open questions）。
- `2026-10-16T16:30:00Z` — `DSH`(DeepSeek)（前台登录阻断缺陷修复，**经用户单独授权**）: **发现并修复 frontend-web 一处阻断性缺陷：协议勾选框失效 → 前台 UI 谁都登不进去**。
  **触发**：用户问"用户端（账号密码）呢" → 我为前台展示数据补密码时，发现前台登录页即使勾选《用户协议》，提交仍提示「请先阅读并同意《用户协议》和《隐私政策》」，登录请求根本发不出（`/api/auth/login` 从未被调用）。
  **根因**（实测取证）：`frontend-web/src/components/shadcn/Checkbox.vue` 用的是 reka-ui 旧 API —— 传 `:checked` 并监听 `@update:checked`；而本项目装的 **reka-ui 2.11.0 的 `CheckboxRoot` 只认 `modelValue` / `update:modelValue`**（`node_modules/reka-ui/dist/Checkbox/CheckboxRoot.js` 内 `modelValue` + `emits:["update:modelValue"]`）。于是 `checked` 未被当作 prop，而是**漏成普通 HTML 属性**渲染（DOM 实证：`<button checked="false" role="checkbox" aria-checked="false" data-state="unchecked">`），`update:checked` 永不触发 → `LoginView` 的 `agree` 恒为 `false` → `handleLogin` 永远走 line 66 的拦截分支。视觉上勾选框能勾上（reka-ui 内部状态自持），所以**看起来正常、实际废掉**。
  **修复**：仅改该包装组件两行 —— `:checked` → `:model-value`、`@update:checked` → `@update:model-value`（对外仍暴露 `v-model:checked`，**所有调用方零改动**；全工程 `SCheckbox` 仅 `LoginView.vue` 两处使用：记住登录状态 + 协议同意）。
  **复验**：`aria-checked=true / data-state=checked` 且 `checked` 属性不再漏出；**空账号提交改为提示「请输入用户名和密码」**（证明父组件 `agree` 已同步，这正是修复前失败的分支）；随后 `stu_alice` 真实登录 → `POST /api/auth/login → 200 {code:0}` → 跳转首页，localStorage 写入 `campus_market_token` / `campus_market_refresh_token` → 个人中心正常显示昵称/认证/信用分。截图 `gui-test-screenshots/admin-react/60-userweb-login.png`、`61-userweb-home.png`、`62-userweb-profile.png`。
  **附带**（仅隔离临时库 3307 的演示数据）：① 用后端真实 BCrypt 编码器把 4 个展示用户密码统一为 `Passw0rd!`（原先是我灌的占位哈希，不可登录）；② 补 `user_auth` 行，使 alice/bob 的"校园认证"在个人中心一致显示（学号 20230101/20230102，邮箱同名 @stu.example.edu.cn）。前台 dev server 已启动在 **5173**。
  **注**：本次改动超出原"不改 frontend-web"约束，系用户在本轮明确授权后执行；`docs/frontend-admin-react-交接文档.md` 增 §13 记录，并已修正文中"frontend-web 零改动"的表述。
