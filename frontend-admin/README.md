# 校园二手交易平台 · 管理后台（React 版）

`frontend-admin`（Vue3 + Element Plus）的 React 重构版本。**业务逻辑、接口契约、路由与权限矩阵与后端保持完全一致**，仅替换技术栈与视觉风格。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 构建 | Vite 6 + TypeScript（strict） |
| UI | React 19 + Tailwind CSS v4（CSS-first）+ shadcn/ui（Radix，new-york） |
| 图标 | lucide-react |
| 路由 | React Router 7（Data Router + 路由级懒加载） |
| 状态 | Redux Toolkit + RTK Query（自定义 axios baseQuery） |
| 表单 | react-hook-form + zod |
| 图表 | Recharts |
| 提示 | sonner（替代 ElMessage）；shadcn AlertDialog（替代 ElMessageBox） |
| 表格 | 原生语义表格 + 统一分页组件 |

视觉风格：**新复古学院风 / 纸质布告栏**（复古森林绿主色、暖米白纸底、姜黄点缀、纸感纹理、胶带/图章标签、12–16px 大圆角）。装饰仅用于侧栏、顶栏、登录页、卡片头、状态标签与空态；表格与表单正文保持中性高对比以保证后台可读性。

## 开发与构建

```bash
npm install
npm run dev          # http://localhost:5174
npm run typecheck    # tsc --noEmit
npm run build        # 产物输出到 dist/
npm run preview
```

开发服务器端口 **5174**，并将 `/api`、`/upload` 代理到后端 `http://localhost:8080`（与旧版一致）。

## 验收状态（2026-10-16）

- ✅ **运行时联调验收通过**：真实 MySQL（隔离 3307）+ 后端 8080 + 5174 + 无头 Chrome 实测，12 条验收清单全绿，`tsc --noEmit` 与 `vite build` 通过，10 路由回归 0 console error。
- 联调中修正 8 项前端缺陷（管理员/用户/分类标签的状态语义反转、公告发布按钮门禁、导入计数、大屏本周趋势与栅格、强改密刷新绕过），明细见 `../docs/frontend-admin-react-交接文档.md` §11。
- 实测确认响应字段口径与 `src/api/types.ts` **完全一致**（含 `AdminLoginVO` 扁平 + role 小写、`AdminVO.mustChangePassword` 为 0/1、`CreditLog.scoreChange`、`ReportVO.images` 为 JSON 数组、时间 `yyyy-MM-dd HH:mm:ss`）。
- 截图证据：`../gui-test-screenshots/admin-react/`（48 张）；联调脚本与断言报告：`../docs/联调脚本/`。
- ✅ **替换已落地（2026-10-16）**：本应用接管规范目录名 `frontend-admin/`（原 `frontend-admin-react/`），旧 Vue3 + Element Plus 版归档到 `../frontend-admin-vue-archive/` 仅供回滚，详见该目录的 `README-ARCHIVE.md`。

## 与后端契约

- 基址 `/api/admin`，统一响应包装 `{ code, message, data }`，`code === 0` 为成功。
- 分页结构 `PageResult{ list, total, pageNum, pageSize }`。
- 图片上传走完整路径 `POST /api/uploads`（**无** `/api/admin` 前缀），手动携带 `Authorization: Bearer <token>`。
- 鉴权：`POST /auth/login` → `AdminLoginVO`（扁平）；`GET /auth/me` → `AdminVO`；`POST /auth/change-password`；`POST /auth/logout`。
- token 仍存于 `localStorage` 键 **`campus_market_admin_token`**（与旧版兼容）。
- 登录态失效码 `40100 / 40101`：清空登录态并由路由守卫跳转登录页。

### 路由与权限（RBAC）

| 路由 | 页面 | 可见角色 |
| --- | --- | --- |
| `/login` | 登录（含强制改密门禁） | 公开 |
| `/dashboard` | 数据大屏 | super / auditor / operator |
| `/content` | 内容管理 | super / auditor |
| `/report` | 举报工作台 | super / auditor |
| `/user` | 用户管理 | super / auditor |
| `/category` | 分类管理 | super / operator |
| `/operation` | 运营配置 | super / operator |
| `/sensitive-word` | 敏感词库 | super / auditor |
| `/log` | 操作日志 | super / auditor / operator |
| `/admin` | 管理员管理 | super |

> 前端菜单与守卫仅控制可见性；真正的权限边界由后端 `@RequireRole` 保证。

## 目录结构

```
src/
  api/            # RTK Query：baseApi + 按资源分片 + 类型契约 + 上传
  app/            # store / 类型化 hooks
  components/     # 共享组件（PageHeader/Pagination/确认与输入弹窗等）
    ui/           # shadcn/ui 原语
  features/auth/  # authSlice（token 与管理员信息）
  layouts/        # AdminLayout（侧栏 + 顶栏）
  lib/            # 工具：cn / 常量映射 / 格式化
  pages/          # 10 个页面（路由级懒加载）
  router/         # 路由表、守卫、菜单配置
  styles/         # Tailwind v4 主题令牌与纸感样式
```

## 关键行为保留清单

- 内容下架/恢复/删除的状态门禁：
  - 下架：`GOODS=ON_SALE`、`WANT|SWAP=OPEN`
  - 恢复：`GOODS=OFF_SALE`、`WANT|SWAP=CLOSED`
  - 删除：`GOODS=IN_TRANSACTION` 与已 `DELETED` 内容禁止删除
- 举报处置动作可组合；`下架内容` 在 `targetType=USER` 时禁用；`images` 兼容 JSON 字符串与数组。
- 敏感词列表兼容 `PageResult` 与平铺数组两种返回（数组时前端过滤 + 切片分页）。
- 登录页保留 `data-testid="admin-login-username"` 与 `data-testid="admin-login-password"`。
