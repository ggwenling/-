# frontend-web-react · 校园二手交易系统前台（React 版）

> 与 `frontend-admin/` **同技术栈**重写的前台商城，1:1 对齐 `frontend-web/`（Vue3 + Element Plus）的**功能与接口**，
> 视觉统一为「新复古学院风 / 纸质布告栏」，并新增一页落地页。

- **技术栈**：Vite 6 · React 19 · TypeScript(strict) · Tailwind v4 · shadcn/ui(Radix) · React Router 7(Data Router) · Redux Toolkit/RTK Query · sonner · react-hook-form + zod · lucide-react
- **端口**：`5173`（与旧版一致，可并行运行需改端口）
- **后端**：`http://localhost:8080`（`/api`、`/upload`、`/ws` 三个代理）
- **验收结论**：演示动线 §1~§9 全绿 → `docs/frontend-web-react-验收记录.md`

---

## 快速开始

```bash
cd frontend-web-react
npm install
npm run dev        # http://localhost:5173
npm run build      # 产物在 dist/（约 4s，按路由切块）
npx tsc --noEmit   # 类型检查（strict，0 error）
```

后端需先起（默认连本机 3306 的 `campus_secondhand`）：

```bash
cd backend && mvn spring-boot:run
# 或 java -jar target/campus-market-0.1.0-SNAPSHOT.jar
```

### 环境变量（`.env.development`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `VITE_DEMO_MODE` | `true` | 认证页展示「验证码已打印到后端控制台」提示；与后端 `app.demo-mode` 对应 |
| `VITE_WS_URL` | 空 | 覆盖 WebSocket 地址；默认同源 `ws(s)://<host>/ws`（开发由 Vite 代理转发） |

### 代理（`vite.config.ts`）

| 前缀 | 目标 | 备注 |
|---|---|---|
| `/api` | `http://localhost:8080` | 业务接口，`changeOrigin: true` |
| `/upload` | `http://localhost:8080` | 静态图片（后端把 `/upload/**` 映射到上传目录） |
| `/ws` | `ws://localhost:8080` | **`ws: true`**，聊天 WebSocket |

---

## 目录结构

```
src/
├─ api/                 # RTK Query 端点（按后端模块切分）+ 契约类型
│  ├─ types.ts          # 由后端 VO/DTO 源码整理的全局类型（含联合枚举与空值说明）
│  ├─ request.ts        # axios 实例（/api、15s、Bearer 由 getToken() 注入）
│  ├─ baseQuery.ts      # 信封解包 + 错误码语义（40100/40101/40301/40302/…）+ 上传之外的统一提示
│  ├─ baseApi.ts        # tagTypes 定义
│  ├─ authApi/goodsApi/wantApi/swapApi/orderApi/messageApi/reportApi.ts
│  └─ upload.ts         # POST /uploads（FormData 字段名 file），手动复刻错误码语义
├─ app/                 # store 与类型化 hooks
├─ features/
│  ├─ auth/authSlice.ts # token + userInfo（双来源映射，见下）
│  └─ msg/              # unread 轮询 + WebSocket hook
├─ components/          # 业务组件 + shadcn 原语
│  ├─ ui/               # shadcn 组件（button/card/dialog/select/tabs/… 共 24 个）
│  ├─ GoodsCard / PriceText / UserCard / EmptyState / Pagination / StatusBadge
│  ├─ CategoryCascader / ImageUploader / ReportDialog / BannedDialog
│  └─ confirm.tsx / prompt.tsx   # 命令式确认/输入弹窗（替代 ElMessageBox）
├─ layouts/MainLayout.tsx        # 纸感顶栏 + 全局公告条 + 页脚 + 未读轮询
├─ pages/
│  ├─ landing/          # 新增落地页（/）
│  ├─ auth/             # 登录 / 注册 / 校园认证
│  ├─ home/             # 商城主页（/home）
│  ├─ goods/            # 列表 / 详情 / 发布（编辑复用 ?id=）
│  ├─ want/ swap/       # 求购、交换：广场 / 详情 / 发布
│  ├─ order/            # 我的订单 / 订单详情
│  ├─ message/          # 消息中心（会话 + 通知 + 实时聊天）
│  └─ profile/          # 个人中心（个人卡 + 七 Tab）
├─ router/              # routes.tsx（路由表）/ RootShell（标题）/ guards（登录守卫）
├─ lib/                 # auth-storage / constants（枚举文案、分页、错误码）/ format / utils
└─ styles/index.css     # 设计令牌（Tailwind v4 @theme inline）+ 纸感工具类
```

### 设计令牌（与后台完全一致）

森林绿 `#2E5B4E` / 米白 `#F7F2E9` / 卡片 `#FFFCF5` / 姜黄 `#D89A3C` / 陶土 `#C25E3A` / 藏青 `#2A3F5C` / 砖红 `#B4443A` / 图章棕 `#8A5A2B`，圆角 14px。
纸感类：`.paper-card` `.paper-fold` `.dashed-card` `.tape-tag` `.stamp-badge` `.topbar-blur`，前台新增 `.polaroid-card` `.notice-board` `.coupon-card` `.landing-scrim`。
**零外网资源**：系统字体栈 + 纯 CSS 构图（无线稿图、无 Web Font），登录页原 2.26MB 插画改为纸感拼贴。

---

## 与契约强相关的实现约定

1. **信封** `{code,message,data}`，**HTTP 永远 200**：`baseQuery` 解包 `data` 后返回，因此页面里拿到的就是 `data` 本体（不是 `res.data.data`）。
2. **错误码**：`40100/40101` 清 token + 跳登录（带 `redirect`）；`40301` 封禁 → 去重弹窗、**不登出**；`40302` 受限 → warning + 恢复提示；其余 `toast.error(message)`。
3. **token 双存储**：`campus_market_token` / `campus_market_refresh_token`，`remember` 决定 localStorage 还是 sessionStorage，**写入时清另一侧**；读取 localStorage 优先（`lib/auth-storage.ts`）。无 refresh 换发流程（access 2h）。
4. **userInfo 双来源**：登录响应给 `userId`，`GET /users/me` 主键是 `id` → `setProfile` 里显式 `userId: d.id ?? prev`；封禁态来自 `status === 1`。
5. **时间**：`LocalDateTime` 统一 `yyyy-MM-dd HH:mm:ss`；唯一例外是 `LoginVO.bannedUntil`（ISO String）。
6. **接口形状陷阱**（详见 `docs/contracts-frontend-web.md`）：
   - `POST /orders` 返回**整个 `OrderDetailVO`**，新单 id 取 `data.id`
   - `GET /orders` 的 `role` 必填；`GET /reviews` 的 `revieweeId` 必填
   - 报错码区分 40915（重复应约）/40916（重复交换请求）/40907（商品已锁定）/40900（重复举报）/40300（自举报）
7. **按钮由后端驱动**：订单/求购/交换的操作按钮一律用 `canConfirm/canReject/canCancel/canComplete/canReview`、`canOffer/canRequest`，前端**不重算状态机**。

---

## 与旧版 `frontend-web/`（Vue3）的差异

| # | 差异 | 原因 |
|---|---|---|
| 1 | `/` = **落地页**；商城主页迁到 `/home`；Logo 指向 `/`；脏 URL 兜底 `/home` | 需求：新增纸质布告栏落地页 |
| 2 | 登录成功默认跳 `/home`（旧版 `/`）；`?redirect=` 优先逻辑不变 | 承接差异 1 |
| 3 | 商品列表**响应 `route.query` 变化**（旧版只在初始化读一次） | 缺陷修正：否则「在 /goods 内点分类/用顶栏搜索」不刷新，演示动线 §1.4/§1.5 不过 |
| 4 | WebSocket 用 `getToken()`（旧版只读 localStorage） | 缺陷修正：否则 `remember=false` 用户永远连不上 |
| 5 | 删除/下架后回跳 `/home`（旧版 `/`） | 承接差异 1 |
| 6 | 保留旧行为：`?tab=notify` 参数不被消息中心读取；筛选成色只下发单值；图片上限检查在上传之后 | 1:1 保留功能，不擅自改动 |

> 旧版 `frontend-web/` **本任务零改动**（`src` 最新 mtime 仍是 2026-10-16 的一次授权修复）；两套应用可并存对照。

---

## 验收与测试

```bash
# 多上下文浏览器验收（无头 Chrome 需先以 --remote-debugging-port=9222 启动）
$env:NODE_PATH='<全局 node_modules>'
node ..\docs\联调脚本\web-react\pw_multi.cjs ..\docs\联调脚本\web-react\acc2.json --report out.json
```

- 验收记录（§1~§9 逐条 + 契约探针 + 缺陷与差异登记）：`docs/frontend-web-react-验收记录.md`
- 剧本与断言报告：`docs/联调脚本/web-react/`
- 截图：`gui-test-screenshots/web-react/`

---

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| `Invalid hook call` / Radix 读到 null React | 运行中新装依赖或首次访问新 chunk 触发 Vite dep 优化器重跑，react 被拆成两个哈希。**停 dev server → 删 `node_modules/.vite` → 重启** |
| 首次访问某个新路由落到 `/home` | 同上（路由模块图陈旧），清 `.vite` 重启即可 |
| 实时消息收不到 | 检查 devtools Network→WS 是否有 `/ws?token=` 且 readyState=1；确认已登录（未登录不建连） |
| 认证页拿不到验证码 | 演示模式验证码打印在**后端控制台**：`【演示模式】校园邮箱 … 的验证码：xxxxxx` |
| 图片 404 | 确认 `vite.config.ts` 的 `/upload` 代理与后端上传目录一致 |
