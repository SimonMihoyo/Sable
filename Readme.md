# Sablé · 数据合规管理系统

> 面向中小企业的数据安全合规 SaaS 平台 —— 数据资产发现、分类分级、访问审计、合规评估、异常告警、报告生成，一站式管理。

在《个人信息保护法》《数据安全法》持续落地、处罚力度不断加码的背景下，中小企业普遍"知道要合规、却不知道怎么做"。Sablé 把合规要求翻译成可执行的系统能力，让企业用一套轻量工具完成从"摸清数据家底"到"输出合规报告"的闭环。

---

## 核心能力

| 能力 | 说明 |
|------|------|
| **数据资产发现** | 连接业务数据库，自动扫描 Schema / 表 / 字段，建立字段级数据资产清单 |
| **分类分级** | 按法规要求识别个人信息、敏感个人信息、重要数据，支持正则 / 关键词 / 采样 / 组合规则 |
| **访问审计** | 记录对敏感数据的关键访问行为，日志以 SHA-256 哈希链防篡改，支持链路校验 |
| **合规差距评估** | 对标 PIPL / DSL 条文建立检查项，自动 + 人工评估，输出差距与整改建议 |
| **异常检测告警** | 非工作时间访问、越权批量导出等异常行为的规则匹配与通知分发 |
| **合规报告** | 面向监管与面向管理层的报告模板化生成与定期推送 |

---

## 技术栈

| 层级 | 选型 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.3.13 |
| 持久层 | MyBatis-Plus | 3.5.7 |
| 数据库 | PostgreSQL | 14+ |
| 构建 | Maven（Wrapper 3.9.16） | — |
| 前端 | React SPA（独立工程，规划中） | — |

---

## 模块结构

工程采用 Maven 多模块划分，`sable-app` 作为唯一启动模块聚合全部业务模块。

| 模块 | 层次 | 职责 |
|------|------|------|
| `sable-app` | 启动装配 | Spring Boot Application 入口、Web 层配置、数据库初始化脚本 |
| `sable-common` | 公共基础 | 统一响应 `R`、全局异常、业务异常、错误码、分页、租户上下文、摘要工具 |
| `sable-system` | 业务 | 系统管理 —— 用户、角色、租户、配置、认证 |
| `sable-discovery` | 业务 | 数据资产发现 —— 数据源管理、Schema 扫描、敏感数据识别 |
| `sable-classify` | 业务 | 分类分级 —— 规则管理、自动分级、人工修正 |
| `sable-audit` | 业务 | 审计引擎 —— 事件采集、日志存储、异常检测 |
| `sable-assessment` | 业务 | 合规评估 —— 检查项管理、评估执行、整改跟踪 |
| `sable-alert` | 业务 | 告警引擎 —— 规则匹配、事件生成、通知分发 |
| `sable-report` | 业务 | 报告引擎 —— 报告模板、PDF 生成、定期推送 |
| `sable-sdk` | 集成 | 客户端审计采集 SDK —— 供业务系统接入，上报审计事件 |

依赖方向：`sable-app` → 各业务模块 → `sable-common`。

```
sable/
├── doc/                    设计文档与图表（需求 / 架构 / 数据库 / API / 排期）
├── sable-app/              启动模块（含 application.yml、db/migration 迁移脚本、db/dev 开发种子数据）
├── sable-common/           公共基础
├── sable-system/           系统管理
├── sable-discovery/        数据资产发现
├── sable-classify/         分类分级
├── sable-audit/            访问审计
├── sable-assessment/       合规评估
├── sable-alert/            异常告警
├── sable-report/           报告生成
├── sable-sdk/              客户端采集 SDK
├── mvnw / mvnw.cmd         Maven Wrapper
└── pom.xml                 聚合 POM
```

---

## 快速开始

### 环境要求

- JDK 17+
- PostgreSQL 14+（需可创建库与 Schema）
- 无需预装 Maven，使用工程自带的 `./mvnw`

### 1. 初始化数据库

只需要建一个空库。表结构、分区、审计哈希链函数等全部由 **Flyway** 在应用启动时自动创建
（迁移脚本位于 `sable-app/src/main/resources/db/migration/`）：

```bash
createdb -U postgres sable
```

启动应用即可，日志里会出现 `Successfully applied N migration(s)`。

> **存量库接入**：若目标库里已经有表、但还没有 `flyway_schema_history`，
> Flyway 会把现有结构记为 V1 基线然后继续往下走 —— 既不报错也不重建数据。
> 该行为由 `spring.flyway.baseline-on-migrate` 控制，开关时机见 `application.yml` 里的说明。

> **想直接看建表脚本**：`sable-app/src/main/resources/db/migration/V1__init_schema.sql`。
> 它是**不可变基线**，一经应用就不能再编辑；后续任何结构变更都要新建 `V2__xxx.sql`。
> 原因与做法见 `AGENTS.md` 第 4 节。

### 2. 配置数据库连接

连接信息通过环境变量注入，未设置时使用（括号内）默认值：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | `localhost` | 数据库主机 |
| `DB_PORT` | `5432` | 数据库端口 |
| `DB_NAME` | `sable` | 数据库名 |
| `DB_USER` | `sable` | 用户名 |
| `DB_PASSWORD` | `sable` | 密码 |

### 3. 构建并运行

```bash
# 全量构建（跳过测试）
./mvnw clean package -DskipTests

# 运行启动模块
./mvnw -pl sable-app -am spring-boot:run

# 本机开发推荐启用 dev profile：启动时自动写入演示租户/角色/账号，开箱即可登录
./mvnw -pl sable-app -am spring-boot:run -Dspring-boot.run.profiles=dev
```

或在 IDE 中直接运行 `me.kirara.sable.SableApplication`（工程已附带 `.run/SableApplication.run.xml` 运行配置，其中已激活 `dev` profile）。

服务默认监听 `http://localhost:8080`。

### 4. 开发账号（dev profile）

建表脚本只建结构、不插数据（生产库更不该带演示账号），所以**空库起来后是没有账号可登录的**。
启用 `dev` profile 后，Flyway 会额外执行 `db/dev/R__dev_seed.sql`，
写入 1 个演示租户、3 个预置角色和下面 3 个可直接登录的账号：

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `alice` | `Sable@2026` | `SUPER_ADMIN` + `COMPLIANCE_OFFICER` | 主账号，租户内全部权限；一个用户挂两个角色，是多角色模型的活样本 |
| `bob` | `Sable@2026` | `AUDITOR` | 只读，用来验证权限受限时的表现 |
| `carol` | `Sable@2026` | `COMPLIANCE_OFFICER` | 读写但不含 `system:manage` |

该脚本是**可重复迁移**（`R__` 前缀）：改动它就会重新执行一次，把这三个账号复位
（密码还原、解锁、取消软删除）。因此改密码、连续失败锁定这类测试可以放心做。

> ⚠️ **不要在生产环境启用 dev profile** —— 那会把一批密码公开的账号写进生产库。
> 该文件也因此刻意放在 `db/dev/` 而不是 `db/migration/`：后者是所有环境都会扫描的路径。

---

## API 概览

所有接口挂在下列基础路径下，详细请求 / 响应定义见 [`doc/数据合规系统-API接口设计文档.md`](doc/数据合规系统-API接口设计文档.md)。

| 基础路径 | 模块 | 职责 |
|----------|------|------|
| `/auth` | system | 登录、刷新、登出、改密 |
| `/system/users`、`/system/roles`、`/system/tenants`、`/system` | system | 用户 / 角色 / 租户 / 配置管理 |
| `/discovery/sources`、`/discovery` | discovery | 数据源管理与扫描 |
| `/classify/rules`、`/classify/standards` | classify | 分级规则与标准管理 |
| `/audit`、`/audit/events` | audit | 审计日志查询、校验与事件上报 |
| `/assessment` | assessment | 合规评估与整改 |
| `/alert` | alert | 告警规则与事件处理 |
| `/reports`、`/files` | report | 报告生成与附件管理 |

### 统一响应格式

所有接口返回统一结构 `R<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1750000000000,
  "traceId": null
}
```

`code = 0` 表示成功；异常由 `GlobalExceptionHandler` 统一转换为上述结构，业务错误码见 `sable-common` 的 `ErrorCode`。

---

## 关键设计

- **多租户隔离**：Schema 级隔离（每租户独立 Schema）+ 行级安全（RLS）兜底，防止代码缺陷导致的跨租户访问。
- **审计防篡改**：`audit_log` 采用 `prev_hash → hash` 的 SHA-256 链式结构，配合分区表按月滚动，支持链路完整性校验。
- **字段级分类分级**：`db_column` 记录敏感级别、分类、命中规则、脱敏与加密要求，为差异化保护提供依据。
- **分层解耦**：Controller / Service / Mapper 严格分层，模块间通过 `sable-common` 共享基础能力。

---

## 设计文档

| 文档 | 内容 |
|------|------|
| [`数据合规系统-需求分析报告.md`](doc/数据合规系统-需求分析报告.md) | 业务需求、角色、用例 |
| [`数据合规系统-系统架构设计文档.md`](doc/数据合规系统-系统架构设计文档.md) | 分层架构、多租户、非功能指标 |
| [`数据合规系统-数据库设计文档.md`](doc/数据合规系统-数据库设计文档.md) | 表结构、分区、索引、安全策略 |
| [`数据合规系统-API接口设计文档.md`](doc/数据合规系统-API接口设计文档.md) | 接口定义 |
| [`数据合规系统-分析报告.md`](doc/数据合规系统-分析报告.md) | 市场与产品规划 |
| [`系统架构图.mmd`](doc/系统架构图.mmd) / [`用例图.mmd`](doc/用例图.mmd) | Mermaid 图源 |
| [`项目排期-MVP-GBK.csv`](doc/项目排期-MVP-GBK.csv) / `.mpp` | 项目排期 |

---

## 项目状态

> 当前处于**脚手架阶段**：分层骨架、数据结构与接口约定已就绪，核心业务逻辑待实现。

**已完成**

- Maven 多模块脚手架，模块依赖与聚合关系打通
- 各模块 Controller / Service / Mapper / Entity / DTO 分层骨架
- 统一响应 `R`、全局异常处理、错误码、分页、租户上下文过滤器、MyBatis-Plus 自动填充
- 审计日志哈希链写入（`AuditLogServiceImpl.append`）
- 完整数据库 Schema（分区表、约束、索引、加密与哈希链函数）
- 全套设计文档

**待实现（主要 TODO）**

- 认证与鉴权：`AuthServiceImpl` 待接入 Spring Security / JWT
- 分类分级引擎：`ClassifyEngineImpl.match()` 四类规则匹配逻辑
- 审计能力：哈希链校验（`verifyChain`）、按维度统计、异步导出
- 数据资产发现：数据源连接测试与真实扫描
- 告警引擎：规则执行与通知分发
- 报告引擎：模板渲染与 PDF 生成
- 合规评估：自动检查项执行
- 自动化测试：目前仅 `sable-app` 引入 `spring-boot-starter-test`，尚无测试用例
- 前端工程：React SPA 尚未建立

---

## 许可证

本项目采用 [WTFPL](License) 许可。
