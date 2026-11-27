# sched-platform 调度平台

一个基于 **Spring Boot 3 + MyBatis-Plus + MySQL** 的表到表数据同步调度平台：配置驱动的同步引擎、分片并发、执行器心跳与故障转移、个人中心与操作日志审计。同时是一套可运行、可对照代码学习的分层工程范例。

> 当前状态：同步主线 M1~M9 与附加模块（个人中心、文件上传下载、操作日志）均已在代码中落地；鉴权（Spring Security + JWT）与独立执行器模块尚未接入，详见[路线图](#路线图)。

## 功能特性

| 模块 | 能力 |
|---|---|
| 同步引擎 | 配置驱动（任务/字段映射/表列白名单全部读库）、全量同步先删后插（幂等）、批次日志与错误明细 |
| 分片 | 按 `MOD(CRC32(分片列), shardTotal)` 切片，线程池并发执行，单片失败不影响其它片 |
| 规则加工 | 基于 QLExpress 的字段级规则：条件命中后对目标列赋值，支持常量与表达式 |
| 容灾 | 执行器 30s 心跳注册、MySQL 租约选主、僵尸批次扫描与单片重跑（零额外中间件依赖） |
| 个人中心 | 用户增删改查、当前用户资料、改密码（BCrypt）、头像上传、文件上传/分页/下载/预览/删除 |
| 操作日志 | `@OperLog` 注解 + AOP 环绕切面自动落库，记录失败操作、耗时、IP；敏感字段递归脱敏；日志写失败不阻断业务 |
| 可观测 | `/api/health` 探活（库挂返回 down 而非 500）、springdoc Swagger UI、SLF4J 参数化日志 |
| 联调页 | `static/` 下纯原生零构建前端页面，覆盖全部个人中心接口 |

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Java 17、Maven 多模块 |
| 框架 | Spring Boot 3.2.5（Web / Validation / JDBC / AOP / Scheduling） |
| 持久层 | MyBatis-Plus 3.5.5（雪花主键 ASSIGN_ID、乐观锁插件）、JdbcTemplate（引擎与日志批量读写） |
| 数据库 | MySQL 8.0（utf8mb4） |
| 表达式引擎 | QLExpress 3.3.2 |
| 密码哈希 | spring-security-crypto（仅 BCryptPasswordEncoder，不引入 Security 过滤链） |
| 接口文档 | springdoc-openapi 2.3.0 |
| 其他 | Lombok、Jackson |

## 架构

```text
sched-admin      启动装配 · Controller · AOP 切面 · @Scheduled 定时任务 · 静态联调页
      │
sched-core       同步引擎 / 规则引擎 / 批次日志 / 执行器注册与选主 / 文件存储抽象 / 业务 Service
      │
sched-dao        MyBatis-Plus Entity · Mapper
      │
sched-common     Result 统一返回 · 枚举 · @OperLog 注解 · 通用工具
```

同步执行链路：

```text
GET /api/dev/run-shard?taskCode=xxx
  → ShardRunner（线程池，按 t_sync_task.shard_total 并发）
  → ConfigDrivenRunner#runShard（生成分片 WHERE 条件）
  → ConfigDrivenRunner#runOnce
       读取 t_sync_task / t_sync_field_mapping / t_sync_table_whitelist
       → SqlBuilder 构建 SELECT / INSERT（表列名白名单校验 + 值参数化）
       → 先 DELETE 目标分片 → 分批 SELECT 源表 → RuleEngine 规则加工 → 批量 INSERT
       → BatchLogService 记批次、ErrorDetailService 记错误明细
```

M8 只做「数据切片 + 单机线程池并发」；M9 的执行器注册、选主与故障转移把单片重跑接回 `ShardRunner#submitOne`。

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 2. 准备数据库

```sql
CREATE DATABASE IF NOT EXISTS sched_platform DEFAULT CHARACTER SET utf8mb4;
```

执行仓库内脚本：

```text
sql/个人中心/personal_center.sql   -- t_sched_user、t_sched_file
sql/操作日志/oper_log.sql          -- t_sched_oper_log
```

> 注意：同步主线使用的 `t_sync_*` 共 10 张表（任务、字段映射、列白名单、规则三件套、批次日志、错误明细、执行器、租约锁）目前**未提供建表脚本**，只跑个人中心/操作日志可跳过；需要跑同步引擎时按仓库外的设计文档建表。这是当前已知的仓库完整性问题。

### 3. 启动

数据库口令通过环境变量注入（配置文件中的 `1234` 仅为本地演示默认值）：

```powershell
$env:MYSQL_PASSWORD="你的口令"
mvn -q -DskipTests clean package
java -jar sched-admin/target/sched-admin-1.0.0.jar
```

或开发模式：

```powershell
mvn -q -DskipTests install
mvn -pl sched-admin spring-boot:run
```

默认端口 `8080`，数据库连接见 `sched-admin/src/main/resources/application.yml`。

### 4. 访问

| 地址 | 说明 |
|---|---|
| http://localhost:8080/ | 原生联调页（个人中心全流程） |
| http://localhost:8080/swagger-ui/index.html | Swagger UI |
| http://localhost:8080/api/health | 探活，返回 `status` 与 `db` 状态 |

## 接口一览

| 方法 | 路径 | 说明 | 审计 |
|---|---|---|---|
| GET | `/api/health` | 服务与数据库探活 | - |
| GET | `/api/dev/run-once` | 最小同步演示（单方法硬编码版） | - |
| GET | `/api/dev/run-once-config?taskCode=` | 配置驱动全量同步 | - |
| GET | `/api/dev/run-shard?taskCode=` | 按库中 `shard_total` 并发分片同步 | - |
| POST | `/api/user` | 新增用户 | ✔ |
| PUT | `/api/user/{userId}` | 修改用户 | ✔ |
| DELETE | `/api/user/{userId}` | 逻辑删除用户 | ✔ |
| GET | `/api/user/{userId}` | 用户详情 | - |
| GET | `/api/user/page` | 用户分页（关键字） | - |
| GET | `/api/user/me` | 当前登录用户资料 | - |
| PUT | `/api/user/me` | 修改本人资料 | ✔ |
| PUT | `/api/user/me/password` | 修改本人密码（BCrypt） | ✔ |
| POST | `/api/user/me/avatar` | 上传头像（图片白名单） | ✔ |
| POST | `/api/file/upload` | 上传文件（扩展名白名单 + 大小校验） | ✔ |
| GET | `/api/file/page` | 本人文件分页 | - |
| GET | `/api/file/{fileId}/download` | 下载（强制 attachment + 归属校验） | ✔ |
| GET | `/api/file/{fileId}/preview` | 预览（仅图片） | - |
| DELETE | `/api/file/{fileId}` | 删除文件记录与磁盘对象 | ✔ |
| GET | `/api/oper-log/page` | 操作日志分页（关键字） | - |

除 `/api/dev/run-once`（早期演示接口，直接返回 `Map`）外，其余接口统一返回 `Result<T>`：`{"code":200,"msg":"成功","data":...}`。

## 核心设计要点

- **配置驱动**：同步任务、字段映射、表列白名单、加工规则全部存库，新增任务不改代码。
- **幂等重跑**：全量同步先按分片删除目标数据再插入，重复执行结果一致；空分片跳过而不误删。
- **SQL 安全**：表名/列名先过白名单集合校验，条件值全部参数化；分片条件只由程序内部生成。
- **零新依赖容灾**：`INSERT IGNORE` + `expire_at` 租约实现选主，心跳用 `ON DUPLICATE KEY UPDATE`，不引入 ZooKeeper / Redis。
- **审计不侵入**：`@OperLog` 只标在 Controller 写操作上，`@Around` 在 `finally` 落库，自身异常仅 `warn`；`password`、`token` 等敏感字段递归替换为 `***`，异常只记类型摘要，不落 SQL 与堆栈。
- **文件安全**：对象名由服务端生成（日期目录 + UUID），用户原始文件名不参与路径，杜绝路径穿越；上传目录不映射为静态资源，所有读取必须经过带归属校验的接口。
- **逻辑删除**：`deleted` 存删除时间戳而非布尔值，唯一键 `(username, deleted)`，支持同名用户删除后重新注册。
- **返回结构收敛**：统一使用 `Result.success()` / `Result.fail()` 静态工厂，不重复手写 builder。

## 目录结构

```text
sched-platform
├── sched-common/            # Result、枚举、@OperLog 注解、CommonUtils
├── sched-dao/               # 实体与 Mapper（用户、文件、操作日志）
├── sched-core/
│   ├── config/              # MyBatis-Plus、密码编码器、配置加载器
│   ├── engine/              # MiniSyncRunner / ConfigDrivenRunner / ShardRunner / SqlBuilder
│   ├── rule/                # QLExpress 规则引擎
│   ├── log/                 # 批次日志、错误明细
│   ├── registry/            # 执行器心跳注册、MySQL 租约选主
│   ├── storage/             # FileStorage 抽象 + LocalFileStorage 实现
│   └── service/             # 用户、文件、操作日志 Service
├── sched-admin/
│   ├── controller/          # Health / Dev / User / File / OperLog
│   ├── aspect/              # OperLogAspect 操作日志切面
│   ├── job/                 # 心跳与故障转移定时任务
│   ├── handler/             # 全局异常处理
│   └── resources/static/    # 零构建联调页
└── sql/                     # 个人中心、操作日志建表脚本
```

## 配置说明

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8080` | 服务端口 |
| `spring.datasource.password` | `${MYSQL_PASSWORD:1234}` | 口令走环境变量，默认值仅本地演示 |
| `spring.servlet.multipart.max-file-size` | `10MB` | 单文件上限，业务侧还会按类型再校验 |
| `spring.task.scheduling.pool.size` | `3` | 心跳与故障转移不能串行，否则会互相误判 |
| `sched.executor.address` | 空（自动探测） | 执行器注册地址，多网卡/容器环境显式指定 |
| `sched.heartbeat.interval-ms` | `30000` | 心跳周期 |
| `sched.heartbeat.timeout-seconds` | `90` | 3 个周期无心跳判定失联 |
| `sched.failover.batch-timeout-minutes` | `30` | 僵尸批次判定，必须大于最长正常任务耗时 |
| `sched.lock.lease-seconds` | `90` | 主租约时长 |
| `sched.file.storage-type` | `local` | 存储类型，切 MinIO/OSS 时只改这里与新增实现 |
| `sched.file.root-dir` | `./data/upload` | 上传根目录，禁止映射为静态资源 |
| `sched.security.dev-user-id` | `1` | 本地固定登录用户，生产必须移除 |

## 安全说明

- `.gitignore` 已忽略 `data/` 上传目录与 `target/`，不要把用户上传物提交进仓库。
- 数据库口令不落仓库明文：使用 `MYSQL_PASSWORD` 环境变量或配置中心加密项。
- 当前**尚未接入鉴权**，`CurrentUserProvider` 使用本地固定用户实现；接入 Spring Security + JWT 后需替换该实现，并给操作日志查询等管理接口加角色限制。
- 下载接口强制 `Content-Disposition: attachment`，预览仅放行图片类型，避免 XSS。

## 已知限制

1. 同步主线 10 张表的 DDL 与演示数据未入仓，换机器无法一键复现同步链路。
2. 未接入 Spring Security / JWT，接口无鉴权；`dev-user-id` 属调试开关。
3. 仅实现全量同步，增量与 UPSERT 未落地；分片列在代码中固定为 `order_id`。
4. 操作日志为同步写库，会给写接口增加一次 INSERT 的耗时，后续可改异步队列。
5. 无单元测试与 CI 流水线，验证依赖手动触发接口。
6. 单机线程池分片，独立执行器模块与多机压测尚未进行。

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| M1~M7 | 骨架、同步最小闭环、配置驱动、规则引擎、批次日志、容灾基础 | 已完成 |
| M8 | 分片切片 + 线程池并发 + 分片调试入口 | 已完成 |
| M9 | 心跳注册、租约选主、僵尸批次转移重跑 | 已完成 |
| 附加 | 个人中心、文件上传下载、操作日志 AOP 审计、原生联调页 | 已完成 |
| 下一步 | Spring Security + JWT 鉴权、DDL 统一入仓、操作日志异步化、独立执行器模块、单测与 CI | 规划中 |

## 说明

本项目为个人学习与实践工程，演示数据与业务表结构按学习路径逐步演进。详细设计推导与实施步骤见项目配套文档《调度平台手册》《调度平台-实施步骤与接口文档》（本地维护，未随仓库分发）。
