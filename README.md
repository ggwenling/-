# 校园二手交易系统

面向单一校园场景的二手交易平台：**校园邮箱实名认证 + 线下面交 + 轻量订单**，含商品出售、求购、以物易物三种交易形态，配套信用分体系、内容审核与管理后台。

- 需求基线：[需求规格说明书.md](需求规格说明书.md)（v1.0）
- 模块划分与需求映射：[模块清单.md](模块清单.md)（M0 骨架 + M1~M7 业务模块，全部完成）
- 命名规范：[docs/命名规范.md](docs/命名规范.md)（新代码务必遵守）

## 技术栈

| 层 | 选型 |
|----|------|
| 后端 | JDK 17 编译（JDK 21 运行）· Spring Boot 3.5.16 · MyBatis-Plus 3.5.17 · MySQL 8 · Redis · JWT（jjwt）· WebSocket · Knife4j |
| 前台商城 | React 19 · TypeScript(strict) · Vite 6 · Tailwind CSS v4 · shadcn/ui · Redux Toolkit/RTK Query · React Router 7（端口 5173） |
| 管理后台 | React 19 · 同前台技术栈 · Recharts（端口 5174，独立登录 + RBAC 三角色） |

架构原则：**单体分层**（controller / service / mapper），RESTful JSON 通信；不引入微服务 / MQ / ES（PRD §3、§11）。

> 前端演进说明：前台与管理后台最初为 Vue 3 + Element Plus 实现，后均重构为 React 版（业务逻辑、接口契约、路由与权限矩阵与后端保持一致，视觉统一为「新复古学院风 / 纸质布告栏」）。Vue 版历史保留在独立仓库 [campus-secondhand-web-vue](https://github.com/ggwenling/campus-secondhand-web-vue) 与 [campus-secondhand](https://github.com/ggwenling/campus-secondhand)（后端含 Vue 时期提交），本仓库不再包含旧前端源码。

## 目录结构

```
校园二手平台交易系统/
├── README.md                   # 本文件
├── 需求规格说明书.md            # 唯一需求基线（54 条功能需求）
├── 数据库设计文档.md            # 27 表字段级设计 + ER 图 + 设计推断 T1~T12
├── 模块清单.md                  # M0~M7 模块划分与验收标准
├── sql/schema.sql              # 建库脚本（27 表 + 分类/敏感词种子，已实机验证）
├── backend/                    # Spring Boot 后端（单体，26 控制器，详见 backend/README.md）
├── frontend-web-react/         # 前台商城 · React 版（16 路由，详见其 README.md）
├── frontend-admin/             # 管理后台 · React 版（10 页 + RBAC，详见其 README.md）
├── landing/                    # 静态落地页原型（纯 HTML/CSS）
├── docs/                       # 工程文档：契约对照、交接文档、验收记录、联调剧本、演示动线
├── gui-test-screenshots/       # 浏览器验收截图（不入库，本地保留）
└── .context/                   # AI 会话交接文档（ai-handoff.md）
```

## 快速启动

环境要求：JDK 17+（21 运行亦可）、Node 18+、Maven 3.9+、MySQL 8、Redis。

```bash
# 0. 初始化数据库（27 表 + 二级分类种子 + 敏感词种子）
mysql -u root -p < sql/schema.sql

# 1. 后端（端口 8080；密码通过环境变量注入，勿写进配置文件）
cd backend
MYSQL_PASSWORD=你的密码 mvn spring-boot:run
# 可选：初始化超级管理员（首次登录强制改密）
MYSQL_PASSWORD=你的密码 ADMIN_USERNAME=admin ADMIN_PASSWORD=初始密码 mvn spring-boot:run
# 8080 被占用时换端口：加 -Dspring-boot.run.arguments=--server.port=18080

# 2. 前台商城（端口 5173）
cd frontend-web-react
npm install && npm run dev

# 3. 管理后台（端口 5174）
cd frontend-admin
npm install && npm run dev
```

| 入口 | 地址 | 默认账号 |
|------|------|------|
| 健康检查 | http://localhost:8080/api/health | — |
| 接口文档（Knife4j） | http://localhost:8080/doc.html | — |
| 前台商城 | http://localhost:5173 | 自行注册 → 校园认证（演示模式验证码打印在后端控制台） |
| 管理后台 | http://localhost:5174 | 上一步初始化的 admin（首次登录强制改密） |

## 环境变量

| 变量 | 用途 |
|------|------|
| `MYSQL_PASSWORD` / `MYSQL_USERNAME` | MySQL 凭据（dev 默认 root/root，仅本机演示） |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符；prod 必填，dev 有演示默认值） |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 启动时幂等初始化超级管理员（T12，不配则跳过） |

> 演示模式 `app.demo-mode=true`：校园邮箱验证码打印到后端控制台，不依赖真实 SMTP。

## 功能一览

- **前台**：注册/校园邮箱认证、商品发布（敏感词拦截 + 多图上传）、列表筛选/全文搜索/教材专区、收藏、下单与面交订单（状态机 + 超时自动取消）、交易互评与信用分、求购广场（应约→原子接受成单）、以物易物（双确认）、站内实时私信（WebSocket）与通知、举报、个性化推荐。
- **管理后台**：RBAC 三角色（超管/审核员/运营员）权限矩阵、内容巡查处置（下架/恢复/软删）、举报工单组合处置、用户封禁与信用调整、分类/标签/敏感词库管理、轮播/公告运营配置、数据大屏、操作日志审计。

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/contracts-frontend-web.md](docs/contracts-frontend-web.md) | 前后端接口契约对照表（含形状陷阱） |
| [docs/frontend-web-react-交接文档.md](docs/frontend-web-react-交接文档.md) / [README](frontend-web-react/README.md) | 前台 React 版结构与约定 |
| [docs/frontend-admin-react-交接文档.md](docs/frontend-admin-react-交接文档.md) | 管理后台 React 版结构与验收 |
| [docs/frontend-web-react-验收记录.md](docs/frontend-web-react-验收记录.md) | 浏览器实测验收（§1~§9 + 契约探针） |
| [docs/M7-需求覆盖核对表.md](docs/M7-需求覆盖核对表.md) | 54 条需求逐条覆盖情况 |
| [docs/演示动线.md](docs/演示动线.md) | 答辩演示脚本 |
| [docs/联调脚本/](docs/联调脚本/) | Playwright 验收剧本（多 context + WS 帧观测） |

## 里程碑（PRD §10）

- [x] 2026.10 需求定稿 + 数据库设计（27 表 + 实机验证）
- [x] 2026.11 基础框架 + 核心模块（用户 / 商品）
- [x] 2026.12 交易闭环（订单状态机 / 聊天 / 互评与信用分）
- [x] 2027.01 特色板块（求购 / 交换 / 教材专区 / 推荐v1）
- [x] 2027.02 管理后台全模块
- [x] 2027.03~04 测试与收尾（集成测试 / 验收 / 演示动线）
- [ ] 2027.05 答辩
