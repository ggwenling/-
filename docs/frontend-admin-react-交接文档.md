# 交接文档 · frontend-admin 重构为 React 版（frontend-admin-react）

> 交接时间：2026-10-16（代码交付 12:00 / **运行时联调验收完成 15:30**）
> 交接机器：`wenling`（Windows，Node 20.20.0 / npm 10.8.2，npm registry 已指向 `registry.npmmirror.com`）
> 发起方：OpenCode（DeepSeek V4.1 Flash Free）会话
> 联调验收方：DSH（DeepSeek）会话 —— 12 条验收清单全部通过，修正 8 项前端缺陷，详见 §11
> 权威状态文件：`.context/ai-handoff.md`（本文件为可读版详细说明，二者内容一致）

---

## 1. 任务目标

把 `frontend-admin`（Vue3 + Element Plus）重构为 React 技术栈的新应用 **`frontend-admin-react`**，并升级视觉为「新复古学院风 / 纸质布告栏」。

**硬约束（用户明确要求）**

| 约束 | 说明 |
| --- | --- |
| 不改业务逻辑 | 页面功能、状态门禁、校验规则与原版一致 |
| 不改后端 | 接口路径、请求/响应结构、错误码、RBAC 矩阵、上传路径全部保持 |
| 不改 `frontend-web` | 前台应用不在本次范围内 |
| 保留运行参数 | 端口 **5174**；代理 `/api`、`/upload` → `http://localhost:8080` |
| 保留兼容点 | token 存 `localStorage` 键 `campus_market_admin_token`；登录页保留 `data-testid="admin-login-username"` / `admin-login-password` |

**交付策略**：新建独立目录 `frontend-admin-react`，旧 `frontend-admin` 原样保留作为回滚路径，验收通过后再由用户决定替换或并存。

---

## 2. 当前进度（截至 2026-10-16）

### 2.1 已完成

- ✅ 工程脚手架：Vite 6 + React 19 + TypeScript（strict）+ Tailwind CSS v4（CSS-first）+ shadcn/ui（Radix，new-york 风格）+ React Router 7（Data Router）+ Redux Toolkit / RTK Query + Recharts + sonner + react-hook-form / zod。
- ✅ 设计系统 `src/styles/index.css`：复古森林绿 `#2E5B4E` 主色、暖米白 `#F7F2E9` 纸底、姜黄 `#D89A3C` 点缀、砖红 `#B4443A` 危险色；纸面颗粒纹理、纸感卡 `.paper-card`、折角 `.paper-fold`、虚线布告卡 `.dashed-card`、胶带标签 `.tape-tag`、图章徽标 `.stamp-badge`、顶栏毛玻璃 `.topbar-blur`；半径 14px（12–16px 区间）。
- ✅ 22 个 shadcn/ui 原语（button / card / input / textarea / label / badge / separator / skeleton / dialog / sheet / tabs / table / select / checkbox / switch / avatar / tooltip / dropdown-menu / alert-dialog / radio-group / scroll-area / sonner）。
- ✅ 共享业务组件：`PageHeader`、`StatusBadge`、`DateTimeText`、`EmptyState`、`TableSkeleton`、`Pagination`、`PageLoader`、`useConfirm`（命令式确认框，替代 `ElMessageBox.confirm`）、`usePrompt`（命令式文本输入弹窗，替代 `ElMessageBox.prompt`）。
- ✅ 数据层：`baseApi`（单 reducerPath + `injectEndpoints` 分片）+ 自定义 axios baseQuery（统一解包 `{code,message,data}`；`code!==0` 返回 error 并 toast；`code∈{40100,40101}` 或 HTTP 401 清空登录态；注入 `Authorization: Bearer`）。
- ✅ 8 个 api 分片：auth / account / user / report / content / catalog / ops / log，另有 `upload.ts`（走完整路径 `/api/uploads`，手动带 JWT）。
- ✅ 状态层：`authSlice`（token 持久化 + 内存态管理员信息，刷新后由 `/auth/me` 恢复）+ 类型化 hooks。
- ✅ 路由与守卫：`RequireAuth`（未登录跳 `/login?redirect=...`；已登录但无角色信息时拉 `/auth/me` 后放行，消除刷新重定向环）、`RequireRole`（越权提示并回 `/dashboard`）。
- ✅ **10 个页面全部实现**（非占位）：

  | 路由 | 页面文件 | 核心能力 |
  | --- | --- | --- |
  | `/login` | `pages/login/LoginPage.tsx` | 登录 + 不可关闭的强制改密弹窗（40310 门禁） |
  | `/dashboard` | `pages/dashboard/DashboardPage.tsx` | 5 张指标卡 + 待办条 + 周订单折线 + 信用分布饼图 + DAU30 面积图 |
  | `/content` | `pages/content/ContentManagePage.tsx` | 商品/求购/交换三 Tab，状态+关键词筛选，下架/恢复/删除/详情 |
  | `/report` | `pages/report/ReportWorkbenchPage.tsx` | 工单列表 + 详情抽屉（证据图）+ 组合处置弹窗 + 驳回 |
  | `/user` | `pages/user/UserManagePage.tsx` | 用户检索、封禁（时长+理由）、解封、信用调整（super 专属）、详情抽屉含信用流水分页 |
  | `/category` | `pages/category/CategoryManagePage.tsx` | 分类/标签两 Tab，增删改 + 启停开关 |
  | `/operation` | `pages/operation/OperationConfigPage.tsx` | 轮播图（含图片上传）+ 公告（草稿/发布/下线） |
  | `/sensitive-word` | `pages/sensitive-word/SensitiveWordPage.tsx` | 新增/删除/批量导入（自动去重）+ 筛选分页 |
  | `/log` | `pages/log/OperationLogPage.tsx` | 操作日志只读，按 adminId + action 筛选 |
  | `/admin` | `pages/admin/AdminManagePage.tsx` | 管理员分页、新建、编辑（姓名/角色/启停）、重置密码 |

- ✅ 路由级代码分割 + 手动分包（charts / react / vendor / radix / data）。
- ✅ **静态验证通过**：`npx tsc --noEmit` 无错误；`npx vite build` 成功（16s）。
- ✅ 开发服务器已启动并实测可访问：`GET http://localhost:5174/login` → **HTTP 200**，root 挂载正常（后台任务运行中）。
- ✅ `frontend-admin-react/README.md` 已写。
- ✅ **接口契约逐条比对后端源码确认无误**（见 §4）。

### 2.2 运行时联调验收（2026-10-16 15:00~15:30，DSH 会话，已完成 ✅）

- ✅ **真实登录 + 全量接口调用 + UI 走查**：隔离 MySQL 3307 + 后端 8080 + 5174 + 无头 Chrome（CDP+Playwright）实测，12 条验收清单全部通过（§7），48 张截图存 `gui-test-screenshots/admin-react/`。
- ✅ **响应字段口径实测校对**：`src/api/types.ts` 全部字段与真实响应一致（含 `AdminLoginVO` 扁平 + role 小写 + `mustChangePassword` 布尔、`AdminVO.id/mustChangePassword(0/1)`、`CreditLog.scoreChange`、`ReportVO.images` 为 JSON 数组、时间 `yyyy-MM-dd HH:mm:ss`、`Result` + HTTP 恒 200），**无需改类型定义**。
- ✅ **修正 8 项前端缺陷**（4 项高严重度状态语义反转），明细见 §11.2。
- ✅ 静态验证复核：`npx tsc --noEmit` 无错误、`npx vite build` 成功（5.95s）。
- ✅ 10 路由最终回归：**0 console error / 0 page error**。
- ✅ **替换已落地（2026-10-16）**：新应用接管规范目录名 `frontend-admin/`（原 `frontend-admin-react/`），旧 Vue 版归档到 `frontend-admin-vue-archive/`（保留可运行、可一行命令回滚）。详见 §12。
- ❌ 未纳入版本管理（该工程目录**不是 git 仓库**）。
- ❌ 未清理死代码：`src/pages/PlaceholderPage.tsx` 已无引用；`radio-group` / `scroll-area` / `avatar` / `tooltip` 原语已建但暂未使用；`useGetReportDetailQuery` 已声明未调用（与 Vue 旧版行为一致）。

### 2.3 阻塞项

无。但联调需要后端 8080 与 MySQL/Redis 可用（本机 MySQL 服务默认停止，需手动拉起；历史踩坑见 `.context/ai-handoff.md` 环境提示：后台直接启动的 `mysqld` 会在该次调用结束后被回收，必须用后台任务方式启动）。

---

## 3. 目录结构

```
frontend-admin-react/
  package.json  vite.config.ts  tsconfig.json  index.html  .gitignore  README.md
  src/
    main.tsx                     # Provider / RouterProvider / Confirm+Prompt Provider / Toaster
    app/store.ts                 # configureStore（auth + baseApi）
    app/hooks.ts                 # useAppDispatch / useAppSelector（withTypes）
    api/
      types.ts                   # 全部后端契约类型（Result/PageResult/VO…）
      request.ts                 # axios 实例（baseURL=/api/admin，注入 Bearer）
      baseQuery.ts               # 自定义 RTK Query baseQuery（解包 + toast + 401 处理）
      baseApi.ts                 # createApi 根（tagTypes）
      authApi / accountApi / userApi / reportApi / contentApi / catalogApi / opsApi / logApi
      upload.ts                  # POST /api/uploads（非 RTK Query，手动带 token）
    features/auth/authSlice.ts   # token 与管理员信息
    layouts/AdminLayout.tsx      # 侧栏（按角色过滤菜单）+ 顶栏（账户下拉/登出）
    router/
      routes.tsx                 # 路由表（lazy + RequireAuth/RequireRole）
      guards.tsx                 # RequireAuth / RequireRole
      nav.ts                     # 菜单配置（含允许角色）
    components/                  # 业务共享组件（PageHeader/StatusBadge/Pagination/confirm/prompt…）
    components/ui/               # shadcn/ui 原语
    pages/                       # 10 个页面
    lib/utils.ts                 # cn()
    lib/constants.ts             # 角色/状态/动作等映射与着色
    lib/format.ts                # formatDateTime / formatAmount / num
    styles/index.css             # Tailwind v4 主题令牌 + 纸感样式
```

---

## 4. 接口契约对照表（已逐条比对后端源码 ✅）

后端源码位置：`backend/src/main/java/com/campus/market/controller/`。前端 baseURL = `/api/admin`。

### 4.1 认证 `AdminAuthController`（`/api/admin/auth`）

| 方法 | 路径 | 请求 | 响应 | 前端调用点 |
| --- | --- | --- | --- | --- |
| POST | `/login` | `{username, password}` | `AdminLoginVO`（扁平：token/refreshToken/adminId/username/realName/role/mustChangePassword） | `useLoginMutation` |
| GET | `/me` | — | `AdminVO` | `useGetMeQuery`（RequireAuth 恢复会话） |
| POST | `/change-password` | `{oldPassword, newPassword}` | `Void` | `useChangePasswordMutation` |
| POST | `/logout` | — | `Void` | `useLogoutMutation` |

### 4.2 管理员账号 `AdminAccountController`（`/api/admin/accounts`，**super 专属**）

| 方法 | 路径 | 请求 | 响应 |
| --- | --- | --- | --- |
| GET | `/` | query `adminId?, pageNum, pageSize` | `PageResult<AdminVO>` |
| POST | `/` | `{username, password, realName, role}` | `Long` |
| PUT | `/{id}` | `{realName, role, status}` | `Void`（启停复用本接口） |
| POST | `/{id}/reset-password` | `{password}` | `Void` |

### 4.3 用户管理 `AdminUserController`（`/api/admin/users`）

| 方法 | 路径 | 请求 | 说明 |
| --- | --- | --- | --- |
| GET | `/` | query `username?, pageNum, pageSize` | `PageResult<AdminUserVO>`（不含 password） |
| GET | `/{id}` | — | `AdminUserVO` |
| GET | `/{id}/credits` | query `pageNum, pageSize`（默认 **10**，max 50） | `PageResult<CreditLog>` |
| POST | `/{id}/ban` | `{reason, durationDays}`（durationDays 空 = 永久） | — |
| POST | `/{id}/unban` | — | — |
| POST | `/{id}/credit` | `{change, remark}`，**`@RequireRole("super")`** | 仅 super 可调 |

### 4.4 举报 `AdminReportController`（`/api/admin/reports`）

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET | `/` | query `status?(0/1/2), targetType?, pageNum, pageSize` |
| GET | `/{id}` | — |
| POST | `/{id}/handle` | `{actions: string[], result}`，actions ∈ `TAKE_DOWN/WARN/DEDUCT/BAN` |
| POST | `/{id}/reject` | `{result}` |

### 4.5 内容巡查 `AdminContentController`（`/api/admin/contents`）

| 方法 | 路径 | 请求 |
| --- | --- | --- |
| GET | `/` | query `targetType`(必填 GOODS/WANT/SWAP)、`status?, keyword?, pageNum, pageSize` |
| POST | `/{targetType}/{id}/take-down` | `{reason}` |
| POST | `/{targetType}/{id}/restore` | — |
| POST | `/{targetType}/{id}/delete` | `{reason}` |

### 4.6 分类/标签/敏感词 `AdminCatalogController`（类级 `/api/admin`，逐方法权限）

| 方法 | 路径 | 权限 | 请求 |
| --- | --- | --- | --- |
| GET | `/categories` | super+operator | — → `Category[]` |
| POST | `/categories` | super+operator | `{parentId, name, sort}` |
| PUT | `/categories/{id}` | super+operator | `{name, icon, sort, status}` |
| DELETE | `/categories/{id}` | super+operator | — |
| GET | `/tags` | super+operator | — → `Tag[]` |
| POST | `/tags` | super+operator | `{name, sort}` |
| PUT | `/tags/{id}` | super+operator | `{name, sort, status}` |
| DELETE | `/tags/{id}` | super+operator | — |
| GET | `/sensitive-words` | super+auditor | query `keyword?, pageNum, pageSize`（**默认 50，max 200**）→ `PageResult<SensitiveWord>` |
| POST | `/sensitive-words` | super+auditor | `{word}` |
| DELETE | `/sensitive-words/{id}` | super+auditor | — |
| POST | `/sensitive-words/import` | super+auditor | `{words: string[]}` |

### 4.7 运营 + 大屏 `AdminOpsController`（类级 `/api/admin`）

| 方法 | 路径 | 权限 | 请求 |
| --- | --- | --- | --- |
| GET | `/banners` | super+operator | — → `Banner[]` |
| POST | `/banners` | super+operator | `{title, imageUrl, linkUrl, sort}` |
| PUT | `/banners/{id}` | super+operator | `{title, imageUrl, linkUrl, sort, status}` |
| DELETE | `/banners/{id}` | super+operator | — |
| GET | `/notices` | super+operator | — → `Notice[]` |
| POST | `/notices` | super+operator | `{title, content}`（草稿态） |
| PUT | `/notices/{id}` | super+operator | `{title, content}` |
| POST | `/notices/{id}/publish` | super+operator | — |
| POST | `/notices/{id}/offline` | super+operator | — |
| DELETE | `/notices/{id}` | super+operator | — |
| GET | `/dashboard` | 三角色 | — → `DashboardVO` |

### 4.8 操作日志 `OperationLogController`（`/api/admin/operation-logs`，三角色只读）

| 方法 | 路径 | query |
| --- | --- | --- |
| GET | `/` | `adminId?, action?, pageNum, pageSize` → `PageResult<OperationLog>` |

### 4.9 图片上传 `UploadController`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/uploads` | **无 `/api/admin` 前缀**；form-data 字段 `file`；需 `Authorization: Bearer <adminToken>`；返回 `{url, thumbUrl}` |

> ⚠️ 之前的交接记录里提过「admin 上传被拦」的遗留问题，后端后来已放行 ADMIN 主体；接手后请以实测为准。

---

## 5. 已刻意保留的业务行为（回归时重点验证）

1. **内容门禁**
   - 下架可用：`GOODS=ON_SALE`、`WANT|SWAP=OPEN`
   - 恢复可用：`GOODS=OFF_SALE`、`WANT|SWAP=CLOSED`
   - 删除禁止：`GOODS=IN_TRANSACTION` 或内容已 `DELETED`
2. **举报**
   - `images` 兼容 JSON 字符串与数组（`parseImages` 防御式解析）。**实测结论**：后端 `ReportVO.images` 声明为 `List<String>`，线上恒为 JSON 数组（`[]` 兜底），字符串分支实际不可达，保留作防御。
   - `targetType=USER` 时「下架内容」动作禁用
   - 处置动作可多选组合，`result` 必填
3. **敏感词**兼容 `PageResult` 与平铺数组两种返回（数组时前端过滤 + 切片分页）。**实测结论**：后端现为 `PageResult`，数组分支为防御性死代码。
4. **RBAC**：菜单/路由守卫只控可见性，真正边界在后端 `@RequireRole`；越权访问前端做提示 + 回 `/dashboard`
5. **强改密门禁**：`mustChangePassword=1` 时登录后弹出不可关闭（禁 Esc / 禁点外遮罩）弹窗，改密成功才放行；后端拦截器同时只允许该账号访问 `/api/admin/auth/**`
6. **错误提示语义**：每个失败请求 toast 一次（在 `baseQuery` 统一处理），与原版 `ElMessage.error` 行为一致

---

## 6. 接手步骤

```powershell
# 1) 安装依赖（如未安装）
cd "D:\毕设\校园二手平台交易系统\frontend-admin"   # 2026-10-16 起规范目录名（原 frontend-admin-react）
npm install

# 2) 类型检查 / 构建（作为门禁，当前均通过）
npx tsc --noEmit
npx vite build

# 3) 启动开发服务器（5174，代理 /api 与 /upload 到 8080）
npm run dev
```

```powershell
# 4) 启动后端依赖（历史踩坑：mysqld 必须用后台任务方式启动，否则调用结束即被回收）
#    MySQL → D:\mysql\bin\mysqld.exe --console（或服务方式，需管理员）
#    后端 → cd backend; mvn spring-boot:run
# 5) 冒烟：GET http://localhost:8080/api/health 应返回 {code:0,...}
```

然后按 §7 验收清单逐条走查。**发现问题只改前端**（后端与前台应用禁止改动）。

---

## 7. 验收清单（联调实测结果，2026-10-16 15:30）

- [x] `npx tsc --noEmit` 与 `npx vite build` 均通过（构建 5.95s）
- [x] super 账号登录成功并落到 `/dashboard`；**刷新页面不跳登录**（验证 `/auth/me` 恢复 + 守卫）
- [x] 三个角色（super / auditor / operator）分别登录：侧栏仅显示权限矩阵内菜单；手动访问越权路由被重定向回 `/dashboard` 并给出提示（实测 auditor→`/category`、operator→`/user`，toast「当前角色无权访问该页面」）
- [x] `mustChangePassword=1` 的账号：登录后被改密弹窗拦住（Esc 无效 / 无关闭按钮 / 无遮罩误关，`min 8 位` 与 `两次不一致` 校验生效），改密成功后正常进入后台；**并修复了"刷新可绕过门禁"的漏洞**
- [x] 10 个页面逐一走查：分页（95→5 页 / 97→5 页）、筛选、重置、弹窗、确认框、**写操作后列表自动刷新**（RTK Query 标签失效）全部正常
- [x] 内容管理三条门禁（下架/恢复/删除）按状态正确启用/禁用，操作后按钮态即时翻转
- [x] 举报工作台：证据图正常渲染（后端实为 JSON 数组，实测 naturalWidth=900 真实加载）；USER 目标时「下架内容」禁用并标注「不适用」；组合处置（警告+扣分）与驳回（确认→理由）后列表刷新且落操作日志
- [x] 用户管理：信用分按钮仅 super 可点（auditor 实测 `disabled` + `title="仅超级管理员可调整信用分"`）；封禁 7 天/解封生效；详情抽屉信用流水分页可用（+5 调整后 12→13 条）
- [x] 运营配置：轮播图上传走 `/api/uploads` 且带 Bearer，返回 url 可预览（表单与列表缩略图均真实加载）；公告 草稿→发布→下线 流程正确，**已下线公告不再显示「发布」**
- [x] 敏感词库：筛选、分页、新增、删除、批量导入（去重，toast 用后端实际新增数）正常
- [x] 管理员管理：新建（初始密码 ≥8 位校验、创建后为须改密）、编辑（姓名/角色/启停语义已纠正）、重置密码
- [x] 旧 `frontend-admin` 的替换/并存方案落地 —— **已按用户决定执行"替换"**：React 版接管 `frontend-admin/`，旧 Vue 版归档为 `frontend-admin-vue-archive/`（§12）

---

## 8. 风险与注意事项

> **2026-10-16 15:30 联调后更新**：原「零运行时验证」风险已消除（12 条清单全绿，字段口径逐条实测校对一致）；下面的第 1、2 条改为留档说明，其余条目仍然有效。

1. ~~**最大的风险是「零运行时验证」**~~ → **已实测消除**：§11 记录了对全部 47 个后台端点中的关键路径的真实调用与字段校对；结论是路径、请求体、query 与**响应字段名/类型全部与后端一致**。遗留的唯一数据口径问题是**后端**举报列表 `targetTitle` 跨类型串号（§11.3，前端无法修）。
2. ~~**`DashboardVO` 字段容错已做**~~ → **已实测确认**：`DashboardVO` 13 个字段名与前端 `types.ts` 完全一致，`creditDistribution` 为中文键 `{优秀,良好,一般,受限}`（前端按 `Object.entries` 直接渲染，正确），`ordersWeekTrend` 为「周一→今天」不定长数组（**前端已按实际长度对齐标签**，修复前固定 7 桶导致后半段恒 0）。
3. **Recharts Tooltip 的 `formatter` 类型**用了内联断言 `(v: number) => ...`，升级 recharts 大版本时需复查。
4. **Pagination 组件 `onPageChange(next, size)`** 在部分页面只用了第一个参数（如日志页把 `size` 直接传给 `setPageSize`），改动时注意签名一致。
5. 开发服务器当前以 `npx vite --port 5174 --strictPort` 后台任务运行；若端口被占会直接失败（strictPort），接手时可先确认 5174 是否仍被占用。
6. 本工程目录不是 git 仓库，**改动没有版本保护**，建议接手方先手工备份或在用户同意后 `git init`。
7. `npm registry` 已是国内镜像 `registry.npmmirror.com`，安装速度快；如接手环境不同需注意。

---

## 9. 遗留 / 待用户决策

| # | 问题 | 建议 |
| --- | --- | --- |
| 1 | 旧 `frontend-admin` 替换还是长期并存？ | **已决定：替换**（2026-10-16 用户拍板）。React 版接管 `frontend-admin/`，旧 Vue 版归档为 `frontend-admin-vue-archive/`，见 §12 |
| 2 | 是否要交付「设计令牌 / 组件预览页」作为 M0 验收物？ | 此前提出未获答复，建议做一页 `/__styleguide`（不挂菜单） |
| 3 | 敏感词默认每页条数：后端默认 50、前端现用 20 | 建议统一为 50 |
| 4 | 举报「扣信用分」是否需要参数化分数？ | 后端 `handle` 只收 `actions[]`，扣分幅度由后端决定，前端不引入分数字段 |
| 5 | 分类是否需要三级？ | 后端只支持两级（`parentId` 空或 0 为一级） |
| 6 | 死代码清理：`pages/PlaceholderPage.tsx`、未使用的 4 个 ui 原语 | 建议删除 / 或在 M8 补用 |
| 7 | 是否给 `frontend-admin-react` 建 git 仓库？ | 用户此前对 `frontend-admin` 的结论是「暂不纳入版本管理」，沿用即可 |

---

## 10. 快速参考

| 项目 | 值 |
| --- | --- |
| 应用目录 | `D:\毕设\校园二手平台交易系统\frontend-admin`（2026-10-16 起；原 `frontend-admin-react`） |
| 旧应用目录（已归档，回滚用） | `D:\毕设\校园二手平台交易系统\frontend-admin-vue-archive`（原 `frontend-admin`） |
| 开发端口 | 5174（代理 `/api`、`/upload` → 8080） |
| token 键 | `campus_market_admin_token` |
| 后端基址 | `/api/admin`；上传 `/api/uploads` |
| 构建命令 | `npx tsc --noEmit` / `npx vite build` |
| 入口 | `src/main.tsx` → `src/router/routes.tsx` |
| 主题令牌 | `src/styles/index.css` |
| 契约类型 | `src/api/types.ts` |
| 状态文件 | `.context/ai-handoff.md` |

---

## 11. 运行时联调验收记录（2026-10-16 15:00~15:30，DSH / DeepSeek 会话）

### 11.1 环境与复现

**隔离原则**：全程未触碰用户真实库（3306，root 密码用户掌握）与真实 MySQL 服务；后台重构部分 `backend/`、`frontend-web/`、`frontend-admin/` 零改动（**唯一例外**：2026-10-16 16:30 经用户单独授权修复前台一处阻断登录的组件缺陷，见 §13）。

```powershell
# 1) 隔离 MySQL 3307（datadir 放系统临时目录，--no-defaults 避免读到 D:\mysql\my.ini；必须后台任务方式启动）
D:\mysql\bin\mysqld.exe --no-defaults --initialize-insecure --datadir="$env:TEMP\campus-mysql3307" --console
D:\mysql\bin\mysqld.exe --no-defaults --datadir="$env:TEMP\campus-mysql3307" --port=3307 --mysqlx=0 --console
# 2) 建库（把 schema.sql 复制到纯 ASCII 路径再用 source，避免中文路径/编码问题）
mysql --host=127.0.0.1 --port=3307 -uroot --default-character-set=utf8mb4 -e "source C:/temp/schema_ascii.sql"
# 3) 后端（连 3307；ADMIN_* 触发 AdminInitializer，首登强制改密）
cd backend
$env:ADMIN_USERNAME='superadmin'; $env:ADMIN_PASSWORD='Admin@12345'
java -jar target\campus-market-0.1.0-SNAPSHOT.jar "--spring.datasource.url=jdbc:mysql://localhost:3307/campus_secondhand?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
# 4) 前端
cd ..\frontend-admin; npm run dev      # 5174（2026-10-16 起规范目录名）
# 5) 无头 Chrome + CDP（沙箱内 Chrome 起不来，需非受限模式）
& 'C:\Program Files\Google\Chrome\Application\chrome.exe' --headless=new --remote-debugging-port=9222 --user-data-dir="$env:TEMP\chrome-profile" about:blank
# 6) 浏览器剧本：Playwright 连接既有 Chrome（Node 脚本，require 全局 playwright，NODE_PATH 指向全局 node_modules）
#    const browser = await chromium.connectOverCDP('http://127.0.0.1:9222')
```

**联调种子数据**：4 用户（含 1 封禁 / 2 已认证）、4 商品（覆盖 `ON_SALE`/`OFF_SALE`/`IN_TRANSACTION`/`DELETED`）、2 求购（`OPEN`/`CLOSED`）、2 交换（`OPEN`/`CLOSED`）、5 举报（GOODS+证据图 / USER / WANT / 已处置 / 已驳回）、12 条信用流水（验证分页）、1 轮播、2 公告（草稿+已发布）。

**踩坑清单（下次复现必读）**
1. 受限文件沙箱内 **Chrome 无法启动**：`mojo platform_channel.cc:112] Check failed: 拒绝访问` = 命名管道被拦；需以 `danger-full-access` 拉起 Chrome，再用 `connectOverCDP` 驱动。agent-browser CLI 亦可用，但要先把 `AGENT_BROWSER_SOCKET_DIR` 指到可写目录。
2. 受限沙箱内 **`npx vite build` 报 `spawn EPERM`**（esbuild 服务用管道启子进程），非受限模式复跑即绿 —— 不是代码问题。
3. `$env:TEMP` 会随沙箱策略切换（出现/消失 `dsh-xxxx` 子目录），脚本一律写绝对路径。
4. `mysqld` 必须后台任务方式启动，否则调用结束即被回收（历史踩坑重现）。
5. Playwright `hasText` 按 `textContent` 归一化匹配：`商品 #1`（innerText 有空格）匹配不到 `商品#1`，用连写口径。
6. 登录接口有 **IP 限流 5 次/分钟（成败均计数，错误码 42900）**；批量登录的验收脚本要限速，否则后续登录全被拒。
7. `AdminInitializer` 建的管理员是 `id=1`，若种子数据里 `report.handler_id=1` 引用管理员，**必须先启动一次后端建号再灌种子**，否则外键 1452。

### 11.2 本轮修正的前端缺陷（8 项，全部只改前端）

| # | 严重度 | 文件 | 现象（实测） | 根因 | 处置 |
| --- | --- | --- | --- | --- | --- |
| 1 | 高 | `src/pages/admin/AdminManagePage.tsx` | 全部启用中的管理员显示「已停用」，按钮为「启用」；点击后确认框写「确认启用」却把账号停用 | 后端 `Admin.status: 0=ENABLED / 1=DISABLED`，前端按 0=停用取反 | 新增 `ADMIN_STATUS_ENABLED/DISABLED` 常量，纠正徽标、按钮图标/文案、确认框、编辑弹窗选项、默认值 |
| 2 | 高 | `src/pages/user/UserManagePage.tsx` | 正常用户显示「已封禁」并给「解封」按钮；已封禁用户反而显示「正常」+「封禁」 | 后端 `User.status: 0=正常 / 1=封禁`，前端用 `status === 0` 判封禁 | 新增 `USER_STATUS_NORMAL/BANNED`，纠正列表徽标与详情抽屉「账号状态」 |
| 3 | 高 | `src/pages/user/UserManagePage.tsx` | 已校园认证用户（`authStatus=1`）显示「审核中」，`已认证` 永远不可达 | `AUTH_STATUS` 误用三态 `{0未认证,1审核中,2已认证}`，后端只有 0/1 | 改为 `{0:未认证, 1:已认证}` |
| 4 | 高 | `src/pages/category/CategoryManagePage.tsx` | 30 个启用中的分类/标签全部显示「已禁用」；编辑弹窗「启用」开关的选中态与提交值相反（打开开关反而写 1=禁用） | 后端 `Category/Tag.status: 0=启用 / 1=禁用` | 新增 `CATALOG_STATUS_ENABLED/DISABLED`，纠正徽标、`Switch checked`、`onCheckedChange`、表单默认值 |
| 5 | 中 | `src/pages/operation/OperationConfigPage.tsx` | 已下线公告（status=2）仍显示「发布」按钮，点击必失败（后端只允许 0→1） | 三元判断只区分了 status===1 | 仅 `status===0` 显示「发布」、`===1` 显示「下线」、`===2` 不显示 |
| 6 | 低 | `src/pages/sensitive-word/SensitiveWordPage.tsx` | 批量导入 toast 报「已导入 N 个」用的是前端提交数，与服务端实际新增数不符 | 忽略 `importSensitiveWords` 的返回值 | 用接口返回的实际新增数，并在有重复时给出说明 |
| 7 | 中 | `src/pages/dashboard/DashboardPage.tsx` | 折线图固定 7 桶（周一~周日）取数，而后端只返回「周一→今天」，周四之后恒为 0、卡片标题语义失真；且两张图各占 `grid-cols-5` 的 1 栏，一行空出 3/5 | 桶数写死 + 缺 `col-span` | 按返回数组长度对齐标签、标题改「本周新增订单趋势」；改 `lg:col-span-3` + `lg:col-span-2` |
| 8 | 中 | `src/router/guards.tsx` | 携带 `mustChangePassword` 的会话**刷新页面即可直进后台**，之后每个请求被后端 40310 拦（只弹 toast，界面空转） | 该标记只在 `LoginPage` 登录成功分支消费 | `RequireAuth` 检测到标记 → toast 提示 → `Navigate` 回 `/login` 重新走门禁 |

> 第 1~4 项均与旧 `frontend-admin`（Vue）逐行核对确认**旧版是正确的**，属 React 重写时的回归，已按"业务逻辑与原版一致"的硬约束改回。
> 其余顺带修正：`src/lib/format.ts` 注释更正（后端时间格式实为 `yyyy-MM-dd HH:mm:ss`，非 ISO）、`index.html` 补内联 SVG favicon（消除 console 404 噪声）。

### 11.3 观测到的后端侧问题（受"不改后端"约束，仅记录）

1. **举报列表 `targetTitle` 跨类型串号**（`backend/.../AdminReportServiceImpl.java:104-151`）：`buildVOs` 把 goods / want_post / swap_post / user 的标题合并进**同一个 `Map<Long,String>`（仅以 id 为键）**，id 相同即被后写的类型覆盖。UI 现象：`GOODS#1` 的工单「被举报内容」显示成 `want_post#1` 的标题；同一工单的**详情接口**（单条解析）返回正确标题。旧 Vue 版同样存在，本轮未改后端。建议修复方式：`Map` 的键改为 `targetType + ":" + id`。
2. `creditDistribution` 的键是硬编码中文（优秀/良好/一般/受限），且 `creditScore <= 59` 不含 NULL 分，四桶之和未必等于 `userTotal`（本轮实测 1+2+0+1=4，正常）。
3. `POST /api/admin/users/{id}/credit` 的 `change` 缺失时后端 NPE → 50000（前端已做非 0 校验，UI 不会触发）。

### 11.4 证据索引

| 内容 | 位置 |
| --- | --- |
| 联调截图（48 张，编号 01~50，缺 18/19） | `gui-test-screenshots/admin-react/` |
| 关键截图 | `01-login` / `20-gate-dialog`（强制改密）/ `22-dashboard-after`（大屏布局修复）/ `03-content-goods`（三门禁）/ `24-report-detail-images`（证据图）/ `43-auditor-menu` `45-operator-menu`（RBAC 菜单）/ `47-operator-forbidden-toast`（越权 toast）/ `11-admin-after`（启停语义修复后） |
| 联调脚本与报告（已随工程留存） | `docs/联调脚本/`：`pw_driver.cjs`（Playwright-CDP 剧本驱动）、`act1~act20.json`（剧本）、`rep1~rep20.json`（结构化断言报告）、`verify_api.mjs` / `verify_operator.mjs`（接口契约探针）、`seed_admin_ui.sql`（联调种子数据，注入隔离 3307 库） |

> 复跑提示：`seed_admin_ui.sql` 会 `TRUNCATE` 用户/内容/举报等业务表，**只能对隔离临时库执行**；`pw_driver.cjs` 需先手动拉起带 `--remote-debugging-port=9222` 的 Chrome，并设置 `NODE_PATH` 指向全局 `node_modules`（依赖全局 `playwright`）。

---

## 12. 替换落地记录（2026-10-16，用户决定"替换"）

### 12.1 最终目录形态

| 路径 | 内容 | 说明 |
| --- | --- | --- |
| `frontend-admin/` | **React 版管理后台**（Vite 6 + React 19 + TS + Tailwind v4 + shadcn/ui + RTK Query） | 接管规范目录名，仍跑 5174，代理与 token 键未变 |
| `frontend-admin-vue-archive/` | 旧 Vue3 + Element Plus 版（原 `frontend-admin/`） | 原样归档，`node_modules` 一并保留，可直接 `npm run dev`；目录内新增 `README-ARCHIVE.md` 说明归档与回滚方式 |

- 原名 `frontend-admin-react/` 已不存在（改名为 `frontend-admin/`）；两版都用 5174 端口，**不可同时启动**。
- 旧版未做任何代码改动，仍是替换前的最后状态。

### 12.2 回滚方式（一行一条，可逆）

```powershell
cd "D:\毕设\校园二手平台交易系统"
Rename-Item frontend-admin frontend-admin-react       # 新版让出规范目录名
Rename-Item frontend-admin-vue-archive frontend-admin  # 旧版回到原位
```

> 若 `Rename-Item` 报"访问被拒绝"（目录被某进程占用，本机实测遇到过一次），改用 `Move-Item` 即可完成同一操作。
> 确认不再需要旧版时，直接删除 `frontend-admin-vue-archive/`（含 `node_modules` 约 170MB）。

### 12.3 替换后复验（已执行）

- 从新路径启动 `npm run dev`：`GET http://localhost:5174/login` → **200**，`GET http://localhost:5174/api/health` → `{"code":0,...}`（vite 代理正常）。
- 浏览器实测（无头 Chrome + CDP）：登录 superadmin → 落 `/dashboard` → **刷新仍在 `/dashboard`**（`/auth/me` 恢复会话）→ `/content` 4 行数据 → `/admin` 3 个账号且状态文案为「正常/停用」正确 → **0 console error / 0 page error**。
- 截图：`gui-test-screenshots/admin-react/51-after-replace-login.png`、`52-after-replace-admin.png`。
- 已清理 `frontend-admin/node_modules/.vite`（内含旧绝对路径的依赖预构建缓存），首次启动自动重建。

### 12.4 同步更新的文档

- `README.md`（根）：目录树加归档行、快速启动加"React 版替换 + 回滚命令"说明。
- `模块清单.md`：M0 前端说明标注 React 版与归档目录。
- `frontend-admin/README.md`：验收状态补"替换已落地"。
- `frontend-admin-vue-archive/README-ARCHIVE.md`：新增归档说明。
- `.context/ai-handoff.md`：状态、路径与日志同步。
- `docs/演示动线.md`、`docs/前端shadcn迁移方案.md`：更新与替换相关的过期表述。
- 保持原样（历史记录，不追改）：`docs/M7-需求覆盖核对表.md`（记录 M7 时点的 Vue 占位状态）、`docs/命名规范.md`（`frontend-admin/` 命名规则仍成立）、`.workbuddy/**` 历史会话记忆。

---

## 13. 附带修复：frontend-web 前台登录阻断缺陷（2026-10-16 16:30，经用户单独授权）

> 本节超出"后台重构"原范围。起因是回答用户"用户端账号密码呢"时顺手实测前台登录，发现**前台 UI 谁都登不进去**，经用户明确授权后修复并复验。

### 13.1 现象

在 http://localhost:5173/login 填入正确账号密码、勾选《用户协议》后点击「登录」：

- 勾选框**视觉上能勾上**，但提交时始终弹出「请先阅读并同意《用户协议》和《隐私政策》」；
- 网络面板中 `POST /api/auth/login` **从未发出**（前端在校验分支就 return 了）；
- 直接调接口 `POST /api/auth/login` 是正常的（`code=0`），所以问题纯在前端。

### 13.2 根因（实测取证）

`frontend-web/src/components/shadcn/Checkbox.vue` 使用了 **reka-ui 旧版 API**：

```vue
<!-- 修复前 -->
<CheckboxRoot :checked="props.checked" @update:checked="emit('update:checked', $event)">
```

而项目实际安装的是 **reka-ui 2.11.0**，其 `CheckboxRoot` 只声明 `modelValue` 并只派发 `update:modelValue`
（证据：`frontend-web/node_modules/reka-ui/dist/Checkbox/CheckboxRoot.js` → `modelValue: {...}`、`emits: ["update:modelValue"]`）。

后果链：
1. `checked` 不是该组件的 prop → 被 Vue **当普通 HTML 属性透传**，DOM 实证：
   `<button checked="false" role="checkbox" aria-checked="false" data-state="unchecked">`；
2. `update:checked` 永不触发 → 包装组件从不 `emit('update:checked')`；
3. `LoginView.vue` 的 `v-model:checked="agree"` 因此**恒为 `false`**（line 34 定义、line 65 拦截）；
4. 而勾选框的**视觉状态由 reka-ui 内部自持**（受控 prop 无效时它自己 toggle），所以"看着勾上了、实际没同步"——最难查的一类失效。

### 13.3 修复（2 行，调用方零改动）

```vue
<!-- 修复后：适配 reka-ui 2.11.0，对外仍暴露 v-model:checked -->
<CheckboxRoot :model-value="props.checked" @update:model-value="emit('update:checked', $event)">
```

全工程 `SCheckbox` 仅 `LoginView.vue` 两处使用（记住登录状态、协议同意），故影响面仅限于此，且无需改任何调用方。

### 13.4 复验证据

| 检查项 | 修复前 | 修复后 |
| --- | --- | --- |
| 勾选后 `aria-checked` / `data-state` | true / checked（但父组件未同步） | true / checked |
| 元素上的 `checked` 泄漏属性 | `checked="false"`（说明未被当 prop） | 不再出现 |
| 空账号点登录的提示 | 「请先阅读并同意…」（拦在协议分支） | 「请输入用户名和密码」（**已进入下一分支，证明 `agree` 同步成功**） |
| 正常登录 | 不发请求、停留 `/login` | `POST /api/auth/login → 200 {code:0}` → 跳转首页，`localStorage` 写入 `campus_market_token`、`campus_market_refresh_token` |
| 个人中心 | — | 正常显示昵称/校园已认证/信用分 137、学号与校园邮箱（掩码显示） |

截图：`gui-test-screenshots/admin-react/60-userweb-login.png`、`61-userweb-home.png`、`62-userweb-profile.png`。

### 13.5 顺带补的前台演示数据（仅隔离临时库 3307）

- 4 个展示用户 `stu_alice` / `stu_bob` / `stu_carol` / `stu_dave` 的密码统一为 **`Passw0rd!`**（原先是我灌种数据时的占位哈希，**不可登录**）；做法是先经 `POST /api/auth/register` 借后端真实 BCrypt 编码器产出哈希再回写，避免自造哈希口径不一致。
- 补 `user_auth` 行，使 `stu_alice`（学号 20230101 / alice@stu.example.edu.cn）与 `stu_bob`（20230102 / bob@stu.example.edu.cn）在个人中心的"校园认证"信息一致（此前只设了 `user.auth_status`，导致页面显示"校园已认证"但学号/邮箱为"未认证"）。
- `stu_carol` 未认证且信用分 45（受限档，<60）；`stu_dave` 处于封禁态（含封禁理由，登录可进但业务接口会被 40301 拦）。
- 前台 dev server 已启动：http://localhost:5173（代理 `/api`、`/upload`、`/ws` → 8080）。

### 13.6 两个环境/范围注意点

1. **frontend-web 构建要换临时目录**：`npx vite build` 在本机默认 `%TEMP%` 下会失败，报
   `[vite:esbuild-transpile] remove …\esbuild-<hash>: Access is denied`（vite 5 会为每个转译写临时文件，而本机 `%TEMP%` 能创建却删不掉；`frontend-admin` 用 vite 6 不走这条路径，故不受影响）。规避：

   ```powershell
   $env:TEMP='D:\毕设\校园二手平台交易系统\.buildtmp'; $env:TMP=$env:TEMP
   cd frontend-web; npx vite build      # ✓ built in ~10.6s（用完可删 .buildtmp）
   ```

2. **改动范围交代**：`frontend-web` 当前有 23 项未提交改动（进行中的 shadcn 迁移 WIP，非本次会话产生），且 `src/components/shadcn/` 整个目录在 git 中**尚未被跟踪**。本次会话只在该目录内的 `Checkbox.vue` 上改了 2 行（§13.3），没有触碰其它任何前端文件。
