# 前端 shadcn 迁移方案（含落地页登录功能）

- **版本**：v1.0
- **日期**：2026-10-05
- **依据**：用户需求「落地页加登录功能 + 以后前端改造改用 shadcn 编写」；现有 `landing/campus.html` 视觉基线
- **三项路线决策**（按推荐执行，可随时推翻）：
  1. **shadcn-vue**（社区官方移植版，Vue 3 + Tailwind CSS + Reka UI）——保留 frontend-web 全部业务代码，渐进迁移；不用 React 原版（那意味着 frontend-web 整体重写，答辩前风险不可控）；
  2. **落地页弹窗登录**——landing 迁移为新栈第一个页面，登录是第一个 shadcn 交付物，对接现有 `/api/auth/login`（后端零改动）；
  3. ~~**frontend-admin 暂不迁移**——Element Plus 重度表格应用，迁移收益低；聚焦前台。~~ **已于 2026-10-16 推翻**：管理后台单独重构为 React 版（Vite + React 19 + Tailwind v4 + shadcn/ui + RTK Query），并已通过运行时联调验收；旧 Vue 版归档在 `frontend-admin-vue-archive/`。本文档其余内容（frontend-web / landing 的 shadcn-vue 路线）不受影响。

> **状态更新（2026-10-16）**：本方案中标记"frontend-admin 暂不迁移"的三处表述均已过期（第 3 项决策、第 4 项待办、实施清单）。后台的实际走向见 `docs/frontend-admin-react-交接文档.md`。

> **状态更新（2026-10-17）**：本文档的 **shadcn-vue 渐进路线已被更强的方案取代**——前台也已按用户要求用 **React + shadcn/ui 全量重建**（新建 `frontend-web-react/`，旧 `frontend-web/` 保留不动、零改动）。
> 因此：
> - 第 3 节「阶段 1 落地页迁移 + 登录弹窗（shadcn-vue）」**不再执行**；落地页与登录页已在 `frontend-web-react` 内以纸质布告栏风格实现（落地页是全屏页而非弹窗登录）；
> - 第 4 节「EP → shadcn-vue 映射表」**改为 EP → shadcn/ui(React) 映射**，实际落地对照见本文第 8 节；
> - 第 7 节「明确不做」中的「React 重写 frontend-web」「frontend-admin 迁移」两条**均已发生**（后台 2026-10-16、前台 2026-10-17）。
> 前台新应用的使用手册见 `frontend-web-react/README.md`，交接文档见 `docs/frontend-web-react-交接文档.md`，验收证据见 `docs/frontend-web-react-验收记录.md`。

---

## 1. 现状与目标

| 现状 | 目标 |
|------|------|
| frontend-web：Vue 3 + Element Plus + Pinia，15 页已验收 | 引入 Tailwind CSS v4 + shadcn-vue；新交互一律 shadcn-vue；EP 页面"触达即迁移" |
| landing/campus.html：静态 HTML 原型（校园图版） | 迁移为 frontend-web 的 Vue 路由页 `/`（LandingView），并加登录弹窗 |
| 登录：独立 /login 页（Element Plus） | 保留；landing 弹窗登录与其共用同一后端接口与 store 逻辑 |

## 2. 阶段 0：技术底座（约半天）

1. **安装 Tailwind v4**：`npm i tailwindcss @tailwindcss/vite`，`vite.config.js` 挂 `@tailwindcss/vite` 插件；
2. **与 Element Plus 共存（关键）**：入口 CSS **不导 preflight**（v4 分层导入：
   `@import "tailwindcss/theme.css" layer(theme);`
   `@import "tailwindcss/utilities.css" layer(utilities);`）——EP 样式不被 Tailwind reset 破坏，二者共存；shadcn 新页面不受影响；
3. **shadcn-vue init**：`npx shadcn-vue@latest init`，`components.json` 设 `typescript: false`（工程为 JS）、style=new-york、tailwind.cssVariables=true；自动生成 `src/lib/utils.js`（cn 工具）与 CSS 变量骨架；
4. **主题映射到 landing 色板**（shadcn CSS variables，dark 集）：
   `--background: #060d09`（深绿墨）、`--foreground: #ffffff`、`--primary: #ffffff`（白 pill）+ `--primary-foreground: #0b1a10`、`--muted: #16241a`、`--border: rgba(255,255,255,.10)`、`--radius: 1rem`——登录卡片与 landing 视觉同源；
5. **验收**：`npm run build` 绿；现有 EP 页面（抽 Home、Profile 各一）回归无样式破坏。

## 3. 阶段 1：落地页迁移 + 登录功能（约 1 天）

### 3.1 Landing 路由与页面

- 新路由 `/` = `views/landing/LandingView.vue`（**不嵌 MainLayout**，独立全屏布局）；现有首页移至 `/home`（MainLayout children `path: ''` 改 `path: 'home'`），旧链接由 router redirect 兜底；
- campus.html 的 Ken Burns / scrim / u-h 单位系统原样搬进 SFC scoped 样式（**电影感层保持自定义 CSS，不强转 Tailwind**）；
- 图片 `assets/campus-plaza.png` 移入 `src/assets/`（Vite 打包处理）；
- 顶部导航：已登录态显示昵称 + 进入首页；未登录显示登录按钮。

### 3.2 登录弹窗（第一个 shadcn 交付物）

- 组件：shadcn-vue `Dialog + Card + Input + Label + Button`（Form 校验用受控 Input + 手写规则，暂不引 vee-validate）；
- 交互：`Get Started` / 导航登录按钮 → 打开 Dialog（深绿墨卡片、白色主按钮，与 landing 同源 token）；
- 字段与错误码映射：

| 后端码 | 场景 | 前端表现 |
|--------|------|----------|
| 40006 | 凭证错误 | 表单下方"用户名或密码错误" |
| 40102 | 连续失败锁定 | 提示锁定剩余说明，按钮置灰 |
| 42900 | IP 限流 | toast"操作过于频繁" |
| 40301 | 封禁 | 弹窗展示 banReason/bannedUntil |
| 成功 | AdminLoginVO 同构 LoginVO（token/refreshToken/userInfo） | 复用 `stores/user.js` 的 applyAuth → 跳 `/home` |

- **注册/认证**：Dialog 底部链接跳现有 `/register`、`/verify`（Element Plus 页保留，触达时再迁移）。

### 3.3 落地页导航接线

- About/Features/FAQ/Contact：毕设阶段为 `#` 占位（或锚到 /home 对应区块）；
- 已登录用户访问 `/`：顶部显示"进入首页"，登录按钮隐藏。

## 4. 阶段 2：渐进迁移约定（长期）

1. **以后所有新页面/新组件一律 shadcn-vue**（用户既定要求）；
2. **旧页面触达即迁移**：哪个 EP 页面因需求变更被打开，就顺手迁到 shadcn-vue；
3. **EP → shadcn-vue 映射表**（迁移时照抄）：
   el-button→Button；el-dialog→Dialog；el-input→Input；el-form→受控 Input+Label（复杂表单再引 vee-validate）；el-table→TanStack Table（shadcn 封装）；el-message→sonner（shadcn-vue 内置支持）；el-tabs→Tabs；el-tag→Badge；el-timeline→手写（shadcn-vue 无此原语，信用流水已有自定义实现可搬）；
4. **frontend-admin 暂不迁移**：EP 重度表格应用，等答辩后评估。

## 5. 风险与对策

| 风险 | 对策 |
|------|------|
| Tailwind preflight 破坏 EP 样式 | 阶段 0 的分层导入方案（跳过 preflight），迁移完成前不开启 |
| 双 UI 库并存体积增大 | EP 已全量引入（既有债务）；迁移完成后统一做按需/裁剪（独立优化项） |
| shadcn-vue 组件覆盖缺口（Timeline/复杂表格） | 按映射表手写组合；TanStack Table 官方支持 |
| 多会话并行开发 | Tailwind/CSS 变量配置改动集中在一个文件（style.css），改动需在 handoff 声明 |
| landing 登录后的会话与 EP 页共存 | 完全复用现有 stores/user.js 与 utils/auth.js（token 存储口径不变） |

## 6. 里程碑与验收

| 阶段 | 内容 | 验收 |
|------|------|------|
| M-a（0.5 天） | 底座：Tailwind v4 + shadcn-vue init + 主题映射 | build 绿；EP 页面回归无破坏 |
| M-b（1 天） | LandingView 迁移 + 登录 Dialog + 路由调整 | 未登录访问 `/` 看到校园落地页；弹窗登录成功进 `/home`；错误码逐个实测 |
| M-c（长期） | 后续页面 shadcn-vue 化 | 按第 4 节约定执行 |

## 7. 明确不做（Out of Scope）

> **已被推翻（2026-10-17）**：以下第 1、2 条均已实际执行，保留原文仅作历史记录。

- ~~React 重写 frontend-web~~ → **已执行**：新建 `frontend-web-react/`（旧目录保留，零改动）
- ~~frontend-admin 迁移~~ → **已执行**（2026-10-16，见后台交接文档）
- 登录之外 landing 上新增注册/认证全流程（链接到现有页）→ 前台 React 版已实现完整注册/认证页
- 后端任何改动（登录接口、JWT、错误码全部现成）→ 仍然成立：前台重建**未改后端一行**

---

## 8. 实际落地：EP(Element Plus) → shadcn/ui(React) 对照（2026-10-17）

| Element Plus | 前台 React 版实现 |
|---|---|
| `el-button` | `ui/button.tsx`（含 `asChild`；变体 default/destructive/outline/secondary/ghost/link/accent） |
| `el-input` / `el-input-number` | `ui/input.tsx` + 原生 `type=number`（价格/报价） |
| `el-select` | `ui/select.tsx`（Radix Select）；分类选择另做 `CategoryCascader`（Radix Popover 双列） |
| `el-cascader` | `components/CategoryCascader.tsx`（二级；提交取末级 id） |
| `el-dialog` | `ui/dialog.tsx`（Radix Dialog） |
| `el-message` / `ElMessageBox.confirm` / `prompt` | `sonner` toast + `components/confirm.tsx` / `prompt.tsx`（命令式 Promise API） |
| `el-table` | 原生 `<table>` + Tailwind（应约/交换请求/举报/信用流水；无需 TanStack 的排序分页能力） |
| `el-tabs` | `ui/tabs.tsx` 或页面内 Tab 按钮组（消息中心/个人中心用后者，便于角标） |
| `el-tag` | `ui/badge.tsx`（success/warning/info/accent/soft） |
| `el-carousel` | 手写图集（箭头 + 圆点，420px） |
| `el-steps` | 手写 4 步时间线（`OrderSteps`，`stepActive` 由状态推导） |
| `el-rate` | 手写星级（`Rate`，支持只读/可点） |
| `el-upload` | `components/ImageUploader.tsx` + `api/upload.ts`（`POST /uploads`，字段 `file`） |
| `el-progress` | `ui/progress.tsx`（Radix Progress） |
| `el-pagination` | `components/Pagination.tsx`（含 pageSizeOptions）+ 广场/消息页简易"上一页/下一页" |
| `el-badge` | 自绘圆点角标（顶栏/会话/Tab） |
| `el-empty` | `components/EmptyState.tsx` |

**未引入**：TanStack Table、Recharts、vee-validate——前台无复杂表格/图表，表单用 react-hook-form + zod 即可。
**保留旧行为**：`?tab=notify` 参数不生效、成色筛选单值生效、上传上限检查在上传之后（详见 `frontend-web-react/README.md`「与旧版的差异」）。
