# frontend-web-react 交接文档

> 交接对象：接手「前台商城 React 版」的开发者 / 下一个 AI 会话
> 姊妹文档：`frontend-web-react/README.md`（使用手册）、`docs/frontend-web-react-验收记录.md`（验收证据）、`docs/contracts-frontend-web.md`（前后端契约对照表）、`docs/frontend-admin-react-交接文档.md`（后台版，结构可对照阅读）

---

## 1. 一句话状态

前台 16 个路由（落地页 + 13 业务页 + 3 详情页 / 编辑态复用发布路由）已用 React 全量重建，**功能与接口与 Vue 版 1:1**，视觉为「新复古学院风 / 纸质布告栏」；演示动线 §1~§9 浏览器实测全绿；`tsc --noEmit` 0 error；`vite build` 4s 通过。

## 2. 技术栈与工程约定

| 维度 | 选型 |
|---|---|
| 构建 | Vite 6（`@vitejs/plugin-react`），端口 5173，代理 `/api` `/upload` `/ws(ws:true)` |
| 语言 | TypeScript strict（0 any 逃逸；`src/api/types.ts` 承载全部契约类型） |
| 样式 | Tailwind v4（CSS-first，`@theme inline` 令牌）+ 纸感工具类；**零外网字体/图片** |
| 组件 | shadcn/ui（Radix 原语 24 个，位于 `src/components/ui/`），业务组件在同级 |
| 路由 | React Router 7 Data Router（`createBrowserRouter` + lazy 分页 + `handle.title` 驱动 document.title） |
| 状态 | Redux Toolkit（仅 auth/msg 两个 slice）+ RTK Query（全部服务端数据） |
| 表单 | react-hook-form + zod（校验文案逐字照搬旧版 el-form rules） |
| 提示 | sonner（toast）+ 自研 `confirm.tsx`/`prompt.tsx`（替代 ElMessageBox） |

**代码风格**：文件内先写「行为契约注释」（对齐旧版哪个文件/哪个方法、哪些是刻意差异），再写实现；中文注释，术语沿用 PRD 的需求编号（GDS/REQ/SWP/ORD/CHT/NTF/RPT/CRD）。

## 3. 分层与关键文件

```
浏览器 → router/routes.tsx → layouts/MainLayout.tsx（顶栏/公告/页脚/未读轮询）
                              ↓
                       pages/*（页面：只做编排 + 展示）
                              ↓
        RTK Query 端点（api/*.ts） ← baseApi(tagTypes) ← baseQuery(信封解包+错误码)
                              ↓
                     request.ts（axios /api + Bearer）
```

| 关注点 | 文件 | 说明 |
|---|---|---|
| 契约类型 | `api/types.ts` | 由后端 VO/DTO 源码整理，含联合枚举与「可为 null」标注 |
| 信封与错误码 | `api/baseQuery.ts` | 解包 `data`；40100/40101 清态跳登录；40301 去重弹窗；40302 warning |
| token 存储 | `lib/auth-storage.ts` | 双 key、双 storage、写入清另一侧、localStorage 优先 |
| 登录态 | `features/auth/authSlice.ts` | `setCredentials` / `setProfile`（id↔userId、status↔banned 映射） |
| 未读红点 | `features/msg/useUnreadPolling.ts` | 30s 轮询 `/chats/unread` + `/notifications/unread-count`，登出/封禁即停 |
| 实时聊天 | `features/msg/useChatSocket.ts` | `/ws?token=`、指数退避重连、CHAT_NEW/CHAT_READ/ERROR、只收不发 |
| 命令式弹窗 | `components/confirm.tsx` `prompt.tsx` | `confirm({title,description,confirmText,tone})` → `Promise<boolean>`；`prompt(...)` → `string \| null`（`null`=取消，`''`=空输入仍提交） |
| 上传 | `api/upload.ts` + `components/ImageUploader.tsx` | 字段名 `file`；举报链路带 MIME/5MB 前置校验，商品链路不带（照抄旧版） |
| 设计令牌 | `styles/index.css` | 颜色/圆角/纸感类；改视觉只改这里。首页另有两个**只作用于 `/home`** 的类：`.home-hero-scrim`（轮播双层遮罩 + 左半 `backdrop-filter` 磨砂，用来压掉第 3/4 张图自带的英文排版残影）、`.home-notice-card`（公告卡渐变底）；全局变量与既有类未改 |
| 市场三页共用件 | `components/market/filter-ui.tsx`、`components/market/Pager.tsx` | 纯展示、不含数据逻辑：`Panel` 白卡面板、`Pill` 药丸筛选块、`Segmented` 段控、`SectionLabel` 区块小标题、`FieldText`、`TrustChip` 静态保障标；`Pager` 数字分页器只回调 `onChange(page)`，页码口径仍由各页面计算 |
| 路由过场 | `components/RouteLink.tsx` + `styles/index.css` 尾部 | `RouteLink` / `useRouteNavigate` 是 `<Link>` / `useNavigate` 的薄包装：**只有 pathname 变化**才挂 `viewTransition`（分页/筛选等查询变化不过场）。`index.css` 里 `::view-transition-*(page)` 做 260ms 交叉淡入 + 8px 位移，`topbar` 组冻结不动，`prefers-reduced-motion` 下全部归零，不支持的浏览器走 `@supports not` 的 `.route-fallback` 入场动画。`RootShell` 负责 `<ScrollRestoration/>`、`<html data-nav>`（前进/后退方向）与空闲预热主路由 chunk。**注意**：`navigate(-1)` 与浏览器后退不会触发过场（React Router 不为 POP 启动 VT），属已知取舍 |

## 4. 每个页面的「不可动摇」要点

| 路由 | 要点 |
|---|---|
| `/`（落地页，新增） | 全屏 `public/campus-plaza.jpg` + ken burns + 纸感遮罩；CTA「进入商城」→ `/home` |
| `/home` | 分类宫格 10 类 → `/goods?categoryId=`；猜你喜欢 `recommendHome(8)`；最新发布 12/页；右侧公告 + 热度榜。**视觉 = Stitch 原型版式**：`components/HomeHeroCarousel.tsx` 四图轮播（6s、hover 暂停、`prefers-reduced-motion` 停播），图片是本地静态常量 `/public/banners/banner-1~4-*.jpg`，**不接** `/api/admin/banners`（后台运营配置与它互不干扰）；商品流用 `GoodsCard variant="plain"`（白卡），其余页面仍用默认拍立得卡 |
| `/goods` | 筛选参数**响应 query**；成色只在恰好选 1 项时下发；20/页；教材专区动态「课程名/ISBN」。**视觉 = Stitch「商品专区」**：面包屑 + 在售计数条 + 横向聚合筛选面板（分类药丸 / 预算快捷区间 / 成色药丸 / 教材专区段控）+ 药丸排序 + 四列白卡（`GoodsCard variant="plain"`）+ 数字分页器 |
| `/goods/:id` | 图集/收藏乐观更新/卖家操作（下架确认、删除确认）/相似推荐排除自身/三个举报入口（本人不显示） |
| `/publish` | 二级分类级联取末级 id；教材字段按**一级分类 id=1** 联动；图 1~9 首张封面；0 图拦截 |
| `/want`,`/want/publish`,`/want/:id` | `pageSize=10`、关键词回车触发；帖主可见应约表；接受=原子（帖 DEALT + 生成订单 + 其余拒绝）。**视觉 = Stitch「求购广场」**：深绿 hero（真实求购总数 + 双 CTA）+ 分类药丸/状态段控/排序段控 + 单列宽卡（右预算立柱、底部发布者与应约数）+ 数字分页器 |
| `/swap`,… | 物品对照双栏；差价开关是**数字 1/0** 且关闭清金额；请求表；同意=原子生成 SCHEDULED 订单。**视觉 = Stitch「以物交换」**：深绿 hero + 分类药丸/状态段控 + 卡片内「我提供 ↔ 我想换取」对照块 + 补差价/待处理脚注 + 数字分页器 |
| `/orders` | 我买到的/我卖出的；按钮全由 `canXxx` 驱动；分页 `[20,40,60]`（全站唯一） |
| `/orders/:id` | 4 步时间线（`stepActive` 推导）；SWAP 双确认区；取消原因；评价三态 |
| `/message` | 会话 100/页、消息 20/页倒序转正序；加载更早消息**保持视口**；敏感词红字在输入框上方；商品卡气泡；`?peerUserId=`/`?goodsId=` 入口 |
| `/profile` | 左个人卡 + 七 Tab 懒加载；信用分进度 = `min(max(s,0),150)/150`；举报表四筛选 |
| `/login` `/register` `/verify` | 10 个 `data-testid` 与协议两段文案逐字；`remember` 默认 true；认证页 60s 倒计时 |

## 5. 契约陷阱清单（写新代码前必读）

1. `POST /orders` 返回 **OrderDetailVO**（取 `data.id`），不是裸 id。
2. `GET /orders` 的 `role`、`GET /reviews` 的 `revieweeId` **必填**，缺失即 50000。
3. 列表类接口对多余参数**静默忽略**（如 `/goods/my` 只认分页）。
4. `want.offers` / `swap.requests` 对非帖主是 **null**（不是空数组）。
5. 详情页 `images` 形状：商品是 `[{url,thumbUrl,sort}]`，举报提交是 `string[]`。
6. 分类树只有两级；提交一律取末级 id。
7. `allowDiff` 是 `0/1` 数字；`diffAmount` 仅 `allowDiff=1` 时有值。
8. 业务错误一律 HTTP 200 + 非 0 `code`，不要按 HTTP 状态判断。
9. 上传响应也是信封（`code/data.url`），且**不走 baseQuery**，需手动解包（`api/upload.ts`）。
10. 时间格式见 §2 契约（`bannedUntil` 是唯一 ISO 例外）。

## 6. 验收与回归

- **验收证据**：`docs/frontend-web-react-验收记录.md`（§1~§9 逐条：预期/实测/证据/截图名）
- **可复现剧本**：`docs/联调脚本/web-react/pw_multi.cjs` + `acc1…acc10.json` + 同名 `.report.json`
  - 驱动能力：多 browser context（隔离 storage 模拟第二用户）、按 `page` 路由动作、`api` 探针（用页面 token 直打接口）、**WebSocket 帧级观测**
- **截图**：`gui-test-screenshots/web-react/`（57 张，70~183）
- **本地回归三件套**：`npx tsc --noEmit` → `npx vite build` → 关键路由手测（`/home`、`/goods`、`/goods/1`、`/message`、`/profile`）

## 7. 已知项 / 保留行为 / 未做

| 类型 | 内容 |
|---|---|
| 保留旧行为 | `?tab=notify` 不被消息中心读取（演示动线靠手动切 Tab）；商品筛选成色只能单选生效；图片上限检查在上传之后（多传会被丢弃但已上传）；商品/求购/交换发布无 5MB 前置校验 |
| 数据相关 | 新发布商品不在推荐缓存中时「热度榜/猜你喜欢」可能为空（`RecommendTask` 每小时重算），非前端缺陷 |
| 未纳入 | 未接入 refresh token 换发（前端无刷新流程，与旧版一致）；未引入 Recharts（前台无图表） |
| 未做（可延展） | 未做 e2e 单元测试；未做 i18n；未做 SSR |

## 8. 如何新增一个页面（照此照做即可）

1. `src/api/types.ts` 补 VO/DTO（对照后端源码），必要时新增 `src/api/xxxApi.ts` 并在 `baseApi` 的 `tagTypes` 里加 tag。
2. `src/pages/xxx/XxxPage.tsx`：顶部写「行为契约注释」；用 RTK Query hook 取数；复用 `components/` 里的纸感组件。
3. `src/router/routes.tsx` 注册（注意 `xxx/publish` 必须在 `xxx/:id` 之前），`handle.title` 写页面名。
4. 需要登录的加 `<RequireAuth>`；需要 40100 语义的无需额外处理（baseQuery 统一）。
5. `npx tsc --noEmit` + 手测；补 `docs/联调脚本/web-react/` 剧本与截图。

## 9. 环境注意事项（反复踩过的坑）

1. **Vite 依赖缓存**：运行中新增依赖或首次加载新 chunk 后，可能出现 `Invalid hook call`（双 React 实例）或新路由落到 `/home`。处理：停 dev server → 删 `node_modules/.vite` → 重启。
2. **沙箱内 npm/vite**：esbuild 需要可写 TEMP，必要时 `TEMP/TMP` 指到工作区临时目录。
3. **无头 Chrome**：需 `--remote-debugging-port=9222 --user-data-dir=<独立 profile>`；同一 profile 会复用登录态（测试前先 `localStorage.clear()`）。
4. **验证码**：演示模式打印在后端控制台；若后端不是受托任务（拿不到 stdout），可从 `AuthServiceImpl` 日志/重启为托管任务获取。
5. **不要用 shell 文本替换改含中文的源码**（PowerShell `Set-Content` 默认 ANSI 会把文件写坏）；一律用编辑工具。
