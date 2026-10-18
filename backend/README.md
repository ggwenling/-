# campus-market · 后端服务（Spring Boot 单体）

校园二手交易系统的后端：**单体分层架构**（controller / service / mapper / entity），RESTful JSON API，
JWT 无状态鉴权 + WebSocket 私信推送 + Redis 缓存，全部业务面向"线下面交、平台不经手资金"的轻量交易闭环。

## 技术栈

| 维度 | 选型 |
|---|---|
| 运行/编译 | JDK 21 运行 · 按 JDK 17 编译 |
| 框架 | Spring Boot 3.5.16（Web / Validation / WebSocket） |
| ORM | MyBatis-Plus 3.5.17（分页插件需配套 `mybatis-plus-jsqlparser`） |
| 数据库 | MySQL 8（库名 `campus_secondhand`，27 表，schema 见仓库根 `sql/schema.sql`） |
| 缓存 | Redis（验证码、登录限流、推荐缓存；仅用基础命令） |
| 鉴权 | JWT（jjwt 0.13，access 2h + refresh 7d），`@RequireAuth` / `@RequireRole` 注解 + 拦截器 |
| 实时通信 | 原生 WebSocket（端点 `/ws?token=`，聊天在线推送 + 离线落库） |
| API 文档 | Knife4j 4.5 + springdoc 2.8.17（启动后访问 `/doc.html`） |

## 快速启动

```bash
# 1. 准备数据库（需 MySQL 8 与 Redis）
mysql -u root -p < ../sql/schema.sql     # 建库 campus_secondhand + 27 表 + 种子数据

# 2. 开发模式启动（默认连 localhost:3306，账号 root）
MYSQL_PASSWORD=你的密码 mvn spring-boot:run
# 8080 被占用时：mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080

# 3. 验证
#    http://localhost:8080/api/health  → {"code":0,...,"status":"UP"}
#    http://localhost:8080/doc.html    → Knife4j 接口文档
```

### 配置（application-dev.yml / application-prod.yml）

| 配置 | 环境变量 | 默认值 |
|---|---|---|
| 数据库密码 | `MYSQL_PASSWORD` | `root`（仅 dev） |
| Redis | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | localhost / 6379 / 空 |
| JWT 密钥 | `JWT_SECRET` | dev 演示密钥（**生产必须覆盖**） |
| 管理员初始化 | `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 空（不初始化） |

- **演示模式** `app.demo-mode=true`：校园认证验证码打印到后端控制台，不依赖真实 SMTP。
- **prod 配置全部走环境变量**，仓库不保存任何真实凭据。

## 目录结构

```
src/main/java/com/campus/market/
├─ common/        # Result/ErrorCode 统一响应、全局异常、工具类
├─ config/        # JWT/Redis/MyBatis/Knife4j/WebSocket/管理员初始化(T12)
├─ controller/    # 26 个 REST 控制器（前台 /api/**，后台 /api/admin/**）
├─ service/       # 业务接口 + impl/（订单状态机、信用引擎、聊天、内容处置、推荐…）
├─ mapper/        # MyBatis-Plus Mapper（含 FOR UPDATE 行锁、取号 SQL 等自定义语句）
├─ entity/        # 27 表对应实体（order_info→OrderInfo 避保留字）
├─ dto/ vo/       # 请求/响应契约
├─ security/      # JWT 解析、拦截器、UserAccessGuard（认证+受限校验收敛）、审计切面
├─ websocket/     # 聊天 WS 处理器、握手鉴权、会话注册表
└─ task/          # 定时任务：订单超时扫描 / 推荐缓存重算 / 孤儿数据扫描
```

## 模块与需求映射

| 模块 | 内容 | PRD 需求组 |
|---|---|---|
| M1 用户 | 注册(BCrypt)/登录锁定/校园认证(验证码)/资料 | USR |
| M2 商品 | 发布(敏感词 DFA)/列表搜索/详情/收藏/图片上传 | GDS |
| M3 交易 | 订单状态机/并发锁定/互评/信用分引擎/超时任务 | ORD + CRD |
| M4 消息 | WebSocket 私信/会话/未读数/站内通知 | CHT + NTF |
| M5 特色 | 求购应约(T8 原子接受)/交换双确认/推荐(行为加权) | REQ + SWP + REC |
| M6 后台 | RBAC 三角色/内容处置/举报工单/词库/大屏/审计 | ADM |
| M7 收尾 | 举报入口/孤儿扫描/封禁与受限全局拦截/IP 限流 | RPT + 全局 |

需求编号对照见根目录 [模块清单.md](../模块清单.md)；全部类级 Javadoc 标注 PRD 编号可追溯。

## 关键设计约定

- **不物理删除**：内容表用状态值软删 + `deleted_at/deleted_by_type/deleted_by/delete_reason` 审计四元组（T4）。
- **41 条物理外键**（RESTRICT/RESTRICT）+ 多态引用靠应用层校验与孤儿扫描任务兜底（T3）。
- **派生计数有事实表**：view/want/favorite 计数的变更与事实写入同事务，唯一约束防重复，定时任务兜底重建（T5）。
- **订单号两步式取号**（T11）、**应约/交换请求一帖一条待处理**（T8 生成列唯一约束）。
- 全部写操作走条件 UPDATE（`rows=0` 即并发冲突），无"查了再改"的竞态。

## 测试

```bash
mvn test        # Testcontainers MySQL8 集成测试 + 服务层单测（需本机 Docker）
```
