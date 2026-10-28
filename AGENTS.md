# AGENTS.md

> 本文件是 AI 编程助手在本仓库工作的**强制约定**。开始任何改动前，请完整读一遍。
>
> 目标只有一个：让 AI 的产出**与既有工程保持一致**，而不是自由发挥、另起炉灶。
> 当你的"创意"与本文件冲突时，**以本文件为准**。

---

## 0. 铁律速览（TL;DR）

1. **不引入新依赖**。尤其禁止擅自加入 Lombok、MapStruct、规则引擎（Drools 等）。确需新增，先提出来，等确认。
   > 例外：**Spring Security 与 JJWT 已在团队决策后引入**（用途与装配禁区见 §3.10）。
   > 它们已经在了，所以"引入"这件事不必再问；但**怎么装配**有硬性约束，
   > 不要照着网上的默认配置改。其余依赖仍一律先问。
2. **不引入新技术栈**。语言、框架、数据库以第 1 节表格为准，不得替换或"升级"。
3. **不臆造**。不得编造不存在的表、字段、接口、错误码。一切以 `doc/`、`sable-app/src/main/resources/db/migration/` 下的迁移脚本和现有代码为准。`db/dev/` 下的种子数据是**本地假数据**，只描述"开发库长什么样"，不作为结构或字段的依据。
4. **不重写风格**。沿用现有分层、命名、注释习惯（详见第 3 节）。不要"顺手美化"未提及的文件。
5. **不改公共契约**。`sable-common` 中的 `R`、`ErrorCode`、`BaseEntity`、`PageQuery`/`PageResult` 等为全局约定，不得随意改动其结构或语义。
6. **不新增模块**。模块划分是设计决策，需要新模块时先讨论。
7. **数据库变更走迁移脚本，并同步文档**。改表结构要**新增**迁移脚本，
   且要选对路径：公共表（`public`）→ `db/migration/V<N>__描述.sql`，
   租户表（`tenant_{id}`）→ `db/tenant/V<N>__描述.sql`（见 3.8），
   并同步 `doc/数据合规系统-数据库设计文档.md`。**已提交的迁移文件不可再改**（见第 4 节）。
8. **改完必须自证**。至少保证 `./mvnw -q -DskipTests compile` 通过；能跑测试就更跑（见第 6 节）。

---

## 1. 项目概况

**Sablé 数据合规管理系统** —— 面向中小企业的数据安全合规 SaaS。定位与能力见 `Readme.md`。

| 项目 | 值（不得更改） |
|------|----------------|
| 语言 | Java **17** |
| 框架 | Spring Boot **3.3.13** |
| 持久层 | MyBatis-Plus **3.5.7** |
| 数据库 | PostgreSQL **14+** |
| 构建 | Maven（`./mvnw`，Wrapper 3.9.16） |
| 根包 | `me.kirara.sable` |
| GAV | `me.kirara:sable:0.0.1-SNAPSHOT` |

> **Lombok 全项目未使用。** 不要"为了方便"引入它。所有 DTO / Entity 一律**手写 getter / setter**。

> **已批准的认证栈**（轻量方案，见 `sable-system/pom.xml`）：`io.jsonwebtoken:jjwt-*` `0.12.6`（仅签发 / 校验）+ `org.springframework.security:spring-security-crypto`（仅 BCrypt 密码哈希）。**除这两个之外**，仍不得擅自新增依赖，尤其不要引入完整的 `spring-boot-starter-security`。

---

## 2. 模块与边界

改动要落在**正确的模块**里。依赖方向单向：`sable-app` → 各业务模块 → `sable-common`。

| 模块 | 职责 | 禁止放什么 |
|------|------|-----------|
| `sable-common` | 全局共享：`R`、异常、错误码、分页、`TenantContext`、`DigestUtil` | 业务逻辑、模块专属 DTO |
| `sable-app` | 启动入口、`@Configuration`、Web 层 Filter、`application.yml`、`db/migration/*.sql`（公共表）、`db/tenant/*.sql`（租户表）、`db/dev/*.sql`（本地种子数据） | 业务领域逻辑 |
| `sable-system` | 用户 / 角色 / 租户 / 配置 / 认证 | 其他模块的关注点 |
| `sable-discovery` | 数据源、Schema 扫描、资产清单 | 分类规则、审计写入 |
| `sable-classify` | 分类分级规则与引擎 | 直接扫描数据库 |
| `sable-audit` | 审计日志、哈希链、异常检测 | 报告渲染 |
| `sable-assessment` | 合规评估与整改 | 告警通知 |
| `sable-alert` | 告警规则与事件 | 审计日志落库 |
| `sable-report` | 报告与附件 | 采集上报 |
| `sable-sdk` | 供**外部业务系统**接入的采集 SDK（轻依赖、无 Spring） | 任何 Spring 依赖 |

> 库模块（非 `sable-app`）中 `spring-boot-starter-web` / `-validation` 一律为 `provided` scope —— 保持现状，不要改成 `compile`。
>
> **⚠️ `provided` 意味着「只保证编译可见，不参与打包」** —— 真正的实现在运行期必须由启动模块 `sable-app` 引入，否则编译能过、启动也正常，但功能会静默失效。已踩过的坑：`spring-boot-starter-validation` 只被库模块以 `provided` 声明，`sable-app` 未引入，导致全项目 14 个 `@Valid` 接口的校验注解**全部形同装饰品**（`GlobalExceptionHandler.handleValidation` 从未被触发）。
>
> **规则**：今后任何以 `provided` 声明在库模块的 `starter`，都必须在 `sable-app/pom.xml` 中显式引入（不带 scope，即 `compile`）。新增此类依赖时，自行做一次「fat jar 里有没有实现类」的检查：`unzip -l sable-app/target/*.jar | grep <实现包名>`。

---

## 3. 代码规范

### 3.1 分层与包结构

每个模块固定四层，包名严格对应：

```
me.kirara.sable.<module>
├── controller/        REST 接口（@RestController）
├── service/           接口
│   └── impl/          实现（@Service）
├── mapper/            MyBatis-Plus Mapper（继承 BaseMapper）
├── entity/            数据库实体（@TableName）
├── dto/               请求 / 响应对象
└── engine/            （可选）领域引擎：接口 + *Impl
```

- **每个包必须有 `package-info.java`**（中文注释，说明该包职责）。这是既有惯例（现共 60 个），新增包务必补齐。
- 业务逻辑写进 `service`，`controller` 只做参数接收 / 编排 / 返回 `R`。

### 3.2 命名约定

| 类型 | 约定 | 示例 |
|------|------|------|
| 实体 | 名词，对应表名驼峰 | `ClassifyRule` → `classify_rule` |
| 请求 DTO | `XxxSaveRequest` / `XxxCreateRequest` / `XxxUpdateRequest` / `XxxRequest` | `RuleSaveRequest` |
| 查询 DTO | `XxxQuery`（可继承 `PageQuery`） | `AuditLogQuery` |
| 结果 DTO | `XxxResult` / `XxxResponse` | `RuleMatchResult` |
| 引擎 | 接口 `XxxEngine` + 实现 `XxxEngineImpl` | `ClassifyEngine` / `ClassifyEngineImpl` |
| 表 / 列 | snake_case，`created_at` 等 | 见 `db/migration/` |
| 迁移脚本 | `V<递增整数>__<小写下划线描述>.sql` | `V2__add_alert_rule_scope.sql` |
| 开发种子数据 | `R__<小写下划线描述>.sql`，**只放 `db/dev/`** | `R__dev_seed.sql` |

### 3.3 统一响应与异常

- Controller 一律返回 `R<T>`：`R.ok()` / `R.ok(data)`，失败用 `R.fail(...)`。
- **成功码是 `0`，不是 `200`**（`R.ok()` 里写死）。`code` 是**业务码**，与 HTTP 状态码分属两个
  维度 —— 200 是传输层的值，0 才是跨领域的成功约定（gRPC canonical code `OK = 0`、
  `errno`、进程退出码）。**别因为"看着不习惯"把它改成 200**；前端一律用 `code === 0` 判成功。
  理由与完整错误码表见 API 文档 1.3 节。
- 业务异常抛 `BusinessException`：
  - 命中已有错误码 → `throw new BusinessException(ErrorCode.AUTH_FAILED);`
  - 需要自定义文本 → `throw new BusinessException(code, "消息");`
- **一个语义只对应一个错误码，不要在调用点"借码"。** 同一个条件在不同类里各写一份判断，
  迟早分叉出不同的码（历史上"请求没带租户"就有两份实现：`UserController` 报 40101 还自带
  一段自定义文本、`RoleController` 报 40104，客户端无从分辨）。这类"取不到就抛"的通用判定
  一律收口成**唯一出口方法**，例如需要租户就调 `TenantContext.requireTenantId()`。
- **新增错误码**须落在本模块号段（40001 起，按模块每 100 一段，见 `ErrorCode`），不得复用或跳段，且消息用中文。
- 不要自己写 `try/catch` 返回错误 —— `GlobalExceptionHandler` 已统一兜底。

### 3.4 分页

- 列表接口参数继承 `PageQuery`（已内置 `page` / `size` / `keyword` 与边界保护，`size` 上限 200）。
- 查询用 `LambdaQueryWrapper` + `Page<Entity>`，返回 `PageResult.of(current, size, total, records)`。

### 3.5 实体与数据库

- 实体加 `@TableName("表名")`、主键 `@TableId(type = IdType.AUTO)`。
- 含 `created_at` / `updated_at` 的表，实体**继承 `BaseEntity`**（自动填充，无需手写这两个字段）。
- 需软删除的表，在子类自行声明 `deletedAt` 字段（`BaseEntity` 不含它）。
- 简单 CRUD 用 MyBatis-Plus（`ServiceImpl<Mapper, Entity>`），**不写 XML**；`classpath*:/mapper/**/*.xml` 仅为复杂查询预留。
- Mapper 接口**不需要** `@Mapper` 注解 —— 已由 `@MapperScan("me.kirara.sable.**.mapper")` 统一扫描。
- **JSONB 列**（schema 里共 9 处）在 Java 侧一律是裸 `String`，**不配 TypeHandler**。
  因此有两条约束必须**同时**成立，少一条就写不进去：
  1. **内容得是合法 JSON 文本** —— 一律经 `me.kirara.sable.common.util.JsonArrays.toJsonArray(...)`
     构造。`String.join(",", list)` 会产出 `a:read,b:read` 这种既非合法 JSON 也无法解析的字符串。
  2. **数据源 URL 必须带 `stringtype=unspecified`**（在 `application.yml` 的 `spring.datasource.url` 上，
     **不要删**）。pgjdbc 默认把 String 参数当 `varchar` 下发，而 PG 没有 `varchar → jsonb` 的隐式转换，
     缺了这个参数时**所有** JSONB 写入都会以
     `column "xxx" is of type jsonb but expression is of type character varying` 失败。
     涉及列：`role.permissions`、`tenant.config`、`sys_audit_log.detail`、
     `alert_rule.config`/`notify_channels`/`notify_targets`、`alert_event.trigger_detail`、
     `assessment_item.evidence_files`/`auto_result`。
  > 只满足第 1 条（用 `JsonArrays` 拼对了 JSON）**不足以**写成功 —— 曾因此误判为"已修复"，
  > 单测全绿而接口实际 500。改完务必真打一次接口，理由见 §5.4。

### 3.6 注释

- 类、public 方法使用 **中文 Javadoc**；段落用 `<p>`。
- 遇到确实没实现的地方，保留 `// TODO 说明` 的既有写法（全项目统一用 `TODO` 中文描述），**不要**改成 `FIXME`、`XXX` 或英文。

### 3.7 日志

- 一律用 SLF4J：`private static final Logger log = LoggerFactory.getLogger(X.class);`。
  **禁止 `System.out` / `printStackTrace`** —— 它们绕过日志框架，级别与输出目标都失效。
- **禁止给 mybatis-plus 配 `log-impl`**。配了就等于放弃级别控制：SQL 会绕过 SLF4J
  直接打到 stdout，生产环境关不掉，而 SQL 语句里带着参数值。SQL 日志靠
  `logging.level.me.kirara.sable` 控制（dev 为 DEBUG，生产为 INFO 即不输出）。
- 输出格式与落盘策略**只在** `sable-app/src/main/resources/logback-spring.xml` 里改；
  日志**级别**写在 `application.yml`（dev 覆写在 `application-dev.yml`），不要写进 logback。
  否则环境变量 `LOGGING_LEVEL_*` 会失效。
- 每行日志自带 `traceId` / `tenantId` / `userId`（MDC，键名定义在 `TraceContext`）。
  新写的过滤器或异步线程要参与这套上下文，用 `TraceContext` 写，**不要自造键名**。
  三者为空只表示这行日志不属于任何请求（启动过程、定时任务），不是缺字段。
- **日志里不得出现**：口令与口令哈希、令牌原文、数据源连接串及其口令、个人敏感字段明文。
  要定位就打主键（`userId` / 实体 `id`）。
- 响应体与响应头里的 `traceId` / `X-Request-Id` 和日志里的是同一个值（`TraceIdFilter` 分配，
  调用方自带的安全形态标识会被透传），排查问题时两边能对上号。

### 3.8 多租户

全库分两层。**改表结构、写查询之前，先确认自己面对的是哪一层**：

| | 应用级 | 租户级 |
|---|---|---|
| schema | `public` | `tenant_{id}` |
| 表 | 9 张公共表 | 12 张业务表 |
| 脚本路径 | `db/migration/` | `db/tenant/` |
| 执行次数 | 每个数据库一次（应用启动时） | **每个租户各一次**（建租户时 / 启动期补齐） |
| 内容 | 租户、用户、角色、成员关系、系统配置、系统审计 | 数据源、库表字段资产、分类规则、审计日志、告警、评估、附件 |
| 代码侧 | `sable-system` | 六个业务模块 |

- **两条迁移路径不能混**。混进同一个路径，Flyway 会把业务表建到 `public`，
  之后租户请求会**静默读到 public 那份数据**（既不报错也不隔离）——本仓库此前正是这个状态。
- 租户脚本里**一律不写 schema 前缀**。Flyway 以 `defaultSchema = tenant_{id}` 运行：
  写 `public.xxx` 会让建表跑到公共 schema，写 `tenant_1.xxx` 会让脚本对其他租户失效。
- schema 名只由 `TenantSchemas.schemaName(Long)` 生成（`tenant_{数字 ID}`），
  **不要在别处拼字符串**。用数字 ID 而不是租户 `code`：`code` 是用户可改的业务字段，
  改一次就得搬整库数据。
- 新增 / 修改租户表后，**已在跑的租户不会自动跟上**：要么重启并打开
  `sable.tenant.provision-on-startup`（dev 默认开），要么对目标库手工触发一次供给。
- 业务模块（`discovery` / `classify` / `audit` / `assessment` / `alert` / `report`）的查询
  **必须带租户上下文**。漏带会得到错误码 **40104**，而不是静默的跨租户数据 ——
  这是 `TenantRequiredInterceptor` 的职责，不要关 `sable.tenant.fail-fast-without-tenant`。
  `sable-system` 相反：登录、租户与用户管理**本来就不该**依赖租户上下文。
- **40104 与 40101 是两件事，别因为都在租户号段就合并**：
  - `40104 TENANT_CONTEXT_MISSING` = **请求没带租户上下文**。调用姿势问题（链路漏了租户、
    或客户端没带 `X-Tenant-Id`），属服务端缺陷。需要租户时一律走
    `TenantContext.requireTenantId()`，它是这个语义的**唯一出口**。
  - `40101 TENANT_NOT_FOUND` = **要找的那个租户不存在**（或已软删）。请求内容问题，
    客户端该提示"租户不存在"，参考 `TenantController#detail`。
  - 类比 HTTP：前者像 `401`（没带凭证），后者像 `404`（资源不存在）。
- 租户上下文缺失时**不要**各处自己写 `TenantContext.getTenantId() == null` 判断 —— 统一用
  `TenantContext.requireTenantId()`，否则就是上一条说的"借码"。
- 隔离靠**连接自身的 `currentSchema`**（`TenantRoutingDataSource` + 每租户一个连接池），
  不靠会话级 `search_path`。设计文档 §5.2 给出的
  `jdbcTemplate.execute("SET search_path TO ...")` 样例**在本项目中是错的**：那条连接多半不是
  MyBatis 随后执行 SQL 用的那一条，还容易污染池里连接状态（串租户的来源）——**不要照抄**。
- 连接池**按租户分开**，改 `sable.tenant.pool-max-size` 前先估
  「租户数 × 本值 ≤ 数据库 `max_connections`」。

---

## 3.9 前端（`sable-admin/`）

前端工程与 Java 模块**平级，但不在 Maven 反应堆里**（`pom.xml` 不包含它，`./mvnw` 也不会碰它）。
技术栈见架构文档 §3.2 —— **已获批**，取的是官方 Ant Design Pro **v6.0.3** 模板
（React 19 + antd 6 + Umi Max 4）。铁律 1 里"不引入前端框架"指的是**不要另起炉灶**，
不是禁止在这个工程里干活；但**新增第三方依赖仍然要先提出来**。

改动前端时的约定：

1. **路径统一带 `/api` 前缀**，后端接口本身是**裸路径**（没有 `/api` 也没有 `/api/v1`）。
   `config/proxy.ts` 在开发环境剥掉前缀，生产由 Nginx 处理 —— 所以别在业务代码里写
   `baseURL`，也别给某个接口单独特判前缀。
2. **`R<T>` 的成功码是 `0`**，与后端一致（见 §3.3）。业务失败走 **HTTP 200 + 非 0 code**，
   认证失败走 **HTTP 401 + R 体**。判断成败一律看 `code`，**不要只看 HTTP 状态**。
3. **令牌存储分工不可随意改**：`accessToken` 只驻内存、`refreshToken` 落 `localStorage`。
   所有"会话结束"的路径都必须收口到 `clearTokens()`——分散清理必然出现
   "清了 access 忘了 refresh"，表现为"点了登出、刷新页面又是登录态"。
4. **刷新会签发一对全新令牌，但不会吊销旧的那一个**。`AuthServiceImpl.refresh()` 校验传入的
   刷新令牌（有效期 / 类型 / 用户 / 是否被吊销 / 账号可用）后签发新的一对，**不吊销传入的** ——
   实测同一个 `refreshToken` 连刷两次都返回 `code=0`。所以：调用方应整体覆盖两个令牌
   （否则本地那份不是最新的），但**不要**据此推断"并发刷新会互相作废、用户会被踢下线"。
   并发刷新在客户端收敛为一次（`src/requestErrorConfig.ts` 里那个共享 Promise）是为了
   别重复签发 N-1 对令牌、也别让"最终存下哪一个"变得不确定。
   真正刷新失败只有三种：刷新令牌过期、已被登出吊销、账号被停用/锁定。
5. **请求拦截器必须设 `validateStatus: () => true`**。去掉它，axios 会把 401 直接 reject，
   响应拦截器根本看不到，自动续期永远不触发。
   同时也**不要**在响应拦截器里把 `40003`（令牌非法/缺失）排除在"可刷新"之外：
   刷新页面后内存里的 accessToken 必为空，首屏那个 `/me` 就是以 `401 + 40003「缺少访问令牌」`
   被拒的 —— 它恰恰最需要走刷新，否则每次刷新页面都会被踢回登录页。
   续期成功后的**重放**还有两条硬性要求，少一条都会以"莫名其妙掉登录"的形式出现
   （`src/requestErrorConfig.ts` 的 `replayRequest`）：
   - **必须 `getResponse: true`**。umi 的 `request()` 结尾会 `res.data` 一次，重放若返回的是
     已经 `.data` 过的响应体，调用方会被再 `.data` 一次，拿到 `R` 里层的业务对象 ——
     `res.code === 0` 恒为假。最典型的受害者是 `/me`：`currentUser` 变 undefined →
     路由守卫判定未登录 → **整页刷新后必定退回登录页**（表现为"要登录两次才能进去"）。
   - **必须摘掉旧的 `Authorization` 头**。重放用的是刷新前那份 config，里面带着刚过期的令牌，
     而请求拦截器只在"没有 Authorization"时才注入新令牌 —— 不摘就会拿旧令牌重放，再 401，
     然后被判成"新令牌也被拒"直接登出（表现为"一过 30 分钟就被踢下线"）。
6. **接口类型定义以 `src/services/sable/typings.d.ts` 为准，不以 API 文档为准** ——
   该文档此前多处是"想当然"（登录响应多写了 `tokenType` 与 `user`，`/me` 的 `roles` 写成对象数组）。
   发现不一致时改文档，别改类型去迁就文档。
7. **`src/access.ts` 是显示控制，安全边界在服务端的 `@PreAuthorize`**（见 §3.10）。
   两个方向的误判都要避免：**界面藏起来 ≠ 已受保护**；**界面显示出来 ≠ 一定调得通**
   （令牌里的权限最长 30 分钟才更新，而 `/me` 是按数据库当前状态装配的）。
8. 菜单名、页面文案**直接写中文**，不再走 `menu.xxx` 的 i18n 间接层 —— 只有 zh-CN 一份语言包，
   多一层 key 只会让"页面上显示什么"多绕一道。
9. 模板自带的**外链与埋点一律去掉**（日志中已清）：Google Analytics、阿里云 CDN 的 logo /
   背景图 / 字体。合规产品不该在页面里向第三方域名发起请求。新增视觉资源前先确认这一点。

---

## 3.10 鉴权（Spring Security）

> 认证（你是谁）与鉴权（你能做什么）在本项目是**两套机制**，分工不要混：
> **认证**由 `web/JwtAuthenticationFilter` 自己完成 —— 它要给出 40001/40002/40003
> 这些细分错误码，Security 的内置入口表达不了；**鉴权**由 `@PreAuthorize` 完成。
> 装配在 `config/SecurityConfig`。

### 权限码

- **字典是 `me.kirara.sable.common.Permission`** —— 权限码字符串的唯一定义处。
  另外两份副本：库里 `role.permissions` 的数据、前端 `sable-admin/src/access.ts` 的镜像。
  前两份之间有测试守着（`PermissionDictionaryTest`），前端那份靠人手工同步。
- 接口上一律写 `@PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).XXX)")`，
  **不写裸字符串**。拼错权限码不会编译失败，只会让那个接口对**所有人** 403 ——
  而日志里只有一句"无权限"，看不出是配错了。
- **一律按权限码判，不按角色名判。** authorities 里只装权限码，所以 `hasRole(...)`
  在本项目恒为假。角色只是权限的容器，`FR-6.2.2` 允许租户自建角色 ——
  写死角色名会让自建角色天然失效。
- **没有通配符**（没有 `*`）。新增权限码后必须有角色被显式授予，否则没人能用。
  这是刻意选的方向：漏配表现为 403（可发现、可修），而不是"所有人凭空多了权限"（不可发现）。
- `ControllerAuthorizationTest` 会扫 `me.kirara.sable.system.controller` 下的每个
  `@RestController`，**新控制器忘了挂 `@PreAuthorize` 会让它变红**。
  确实要公开的控制器，加进它的例外清单并写明理由 —— 那个"红"是设计意图，不是障碍。

### 装配禁区（改 `SecurityConfig` 前逐条确认）

1. **认证过滤器必须挂在安全链内部**（`addFilterBefore(..., AnonymousAuthenticationFilter.class)`），
   并且**不能**标 `@Component`。标了会被 Spring Boot 顺带注册成 Servlet 过滤器：
   每个请求跑两遍，且在链外那一次写进安全上下文的东西会被 `SecurityContextHolderFilter`
   清掉 —— 现象是"过滤器明明放行了，接口仍然是 401"。
2. **不要启用 `.cors()`**。跨域由 `WebConfig` 双注册负责（MVC 层 + 压到
   `HIGHEST_PRECEDENCE` 的 `CorsFilter`）。再让 Security 处理一遍会出现第二个 `CorsFilter`
   → 响应里出现重复的 `Access-Control-Allow-Origin` → 浏览器按多值拒收。
   它只在真实跨源请求上出现，是最难查的一类故障。
3. **`spring.security.filter.order=1` 不要改回默认的 `-100`**。它必须夹在
   `TraceIdFilter(0)` 与 `TenantContextFilter(2)` 之间：晚了，认证失败的 401 不带 traceId；
   早了，租户还没写进上下文。
4. **`csrf` 必须关**（无 Cookie、无会话，且不关会让所有写操作变成 403），
   **`sessionManagement` 必须 `STATELESS`**。
5. **响应契约不能退化**：任何拒绝都是"真实 HTTP 状态码 + `R` 体"。三处实现 ——
   `JwtAuthenticationFilter`（401，主路径）、`SecurityExceptionHandler`（403，实际触发点）、
   `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`（兜底，正常情况下不执行）。
   少一处就会有段路径回空体，而前端读不到 `code` 就分不清"令牌过期可以刷新"与"服务挂了"。
   `SecurityErrorResponseTest` 钉着这一条。

### 权限的生效时机

权限装在令牌的 `perm` 声明里，因此改角色或改权限后，最长要等访问令牌过期
（默认 30 分钟）才在接口校验上生效。需要立即生效 → 吊销该用户的令牌（登出走的就是这条路）。
另注意 `GET /system/users/me` 是按**数据库当前状态**装配的，所以"菜单立刻出现、
点进去仍 403"会在刚授权后的一小段时间内出现 —— 这是取舍（见
`AuthServiceImpl#issueTokens`），不是缺陷。

---

## 4. 标准任务配方

**新增一个 REST 接口**
1. 在对应模块 `dto/` 加请求 / 响应类（手写 getter/setter）。
2. 在 `service/` 加接口方法，`service/impl/` 实现。
3. 在 `controller/` 加方法，返回 `R<T>`，`@Valid` 校验入参。
4. 若新增错误分支，在 `ErrorCode` 本模块号段加码。

**数据库结构变更（新增 / 修改表、列、索引、约束）**
1. **先定路径**（见 3.8）：改的是 `public` 下的公共表 → `db/migration/`；
   改的是 `tenant_{id}` 下的业务表 → `db/tenant/`。然后看该路径下现有的最大版本号 N，
   **新建** `V<N+1>__描述.sql`，风格（缩进、约束命名、`COMMENT`、部分索引写法）与现有一致；
   一个文件只做一件事。租户脚本里**不写 schema 前缀**。
2. **绝不修改已存在的迁移文件，包括 `V1__init_schema.sql` 与 `db/tenant/V1__tenant_schema.sql`。** Flyway 会比对每个
   **真正执行过**的迁移的 checksum，改了之后这类环境启动时直接报
   `Migration checksum mismatch for migration version 1` 并拒绝启动（已实测）。
   **⚠️ 但这条自动保护有盲区**：以 baseline 方式接入的库（历史记录 `type = BASELINE`、
   `checksum` 为空）根本不参与比对 —— 在那类库上改了 V1，**不会报错、看起来一切正常**。
   而全新环境（CI、新同事的机器、生产首次部署）会真的执行被改过的 V1，
   于是**各环境的结构悄悄分叉**。盲区恰好覆盖了所有现有环境，分叉却发生在未来环境上 ——
   这正是这条规则最需要靠人守、而不能指望工具拦住的地方。
   改错了的正确补救办法是**再追加一个**新迁移把结构改回去，而不是回头编辑。
3. 同步 `doc/数据合规系统-数据库设计文档.md`（表结构、ER 图、索引说明）。
4. 自证：对着**全新空库**跑一次，确认从零能建出目标结构 —— 见 5.1。

**新增 / 修改开发种子数据**
1. 只改 `db/dev/R__dev_seed.sql`，或另开一个 `db/dev/R__<描述>.sql`。
   **绝不能放进 `db/migration/`** —— 那个路径所有环境都会执行，包括生产；
   而那批账号的密码是公开写在文件里的。
2. 必须**幂等**：所有写入都带 `ON CONFLICT`。`R__` 是可重复迁移，文件一改就会重跑，
   裸 `INSERT` 会在第二次执行时撞唯一约束，让应用**启动失败**。
3. 种子里写密码只写 BCrypt 哈希，并在注释里写明明文（开发账号统一 `Sable@2026`）。
   不允许为了让某个流程"看起来跑通"而把账号造成登不进去的样子 ——
   典型反例是 `mfa_enabled = TRUE`（TOTP 还没实现，登录会被直接拒绝）。
4. 自证：用 `SPRING_PROFILES_ACTIVE=dev` 起一次，拿账号**真实登录**一遍（见 5.2）。

**新增一张表 / 一个实体**
1. 按上面的「数据库结构变更」加迁移脚本。
2. 建 `entity/`（继承 `BaseEntity` 视情况）、`mapper/`、`service/`、`service/impl/`。

**实现一个引擎（如 `ClassifyEngineImpl`）**
1. 保持接口 `XxxEngine` 签名不变，只填 `*Impl`。
2. 规则 / 策略从数据库或注入的 Service 获取，**不要在引擎里硬编码**。
3. 纯逻辑优先写成可单测的形式。

---

## 5. 构建与验证

```bash
# 编译（最低要求，改动后必须通过）
./mvnw -q -DskipTests compile

# 全量构建
./mvnw clean package -DskipTests

# 运行
./mvnw -pl sable-app -am spring-boot:run
```

- 运行需要 PostgreSQL（连接参数见 `Readme.md` 的环境变量表）。**启动时会自动执行未应用的迁移。**
- 当前仅有 `sable-app` 引入了 `spring-boot-starter-test`；若要给其他模块加测试，需先在该模块 `pom.xml` 声明 test 依赖。

### 5.1 验证迁移脚本（改动表结构后必做）

迁移只有在**空库**上才能被完整验证。在已有数据的库上跑，Flyway 会走基线路径直接跳过 V1，
把"从零根本建不出来"的问题掩盖掉 —— 必须另建一个一次性空库来验：

```bash
# 建一次性空库（需有 CREATEDB 权限的账号）
createdb -h localhost -U <superuser> sable_migration_check

# 指向它启动一次，看日志里有没有 "Successfully applied N migration(s)"
DB_NAME=sable_migration_check ./mvnw -pl sable-app spring-boot:run

# 数一下对象是否齐全（对照迁移脚本里的 CREATE TABLE 数量）
psql -h localhost -U <superuser> -d sable_migration_check -c '\dt'

# 验完删掉
dropdb -h localhost -U <superuser> sable_migration_check
```

顺带提醒：本机 `psql` / `createdb` 不在 `PATH`（PostgreSQL 由 Postgres.app 提供），
需要先 `export PATH="/Applications/Postgres.app/Contents/Versions/16/bin:$PATH"`。
默认的应用账号 `sable` **没有** CREATEDB 权限，建临时库要用超级用户。

### 5.2 验证开发种子数据与登录（改动 `db/dev/` 后必做）

```bash
# 一次性空库 + dev profile 起一次
createdb -h localhost -U <superuser> -O sable sable_seed_check
DB_NAME=sable_seed_check SPRING_PROFILES_ACTIVE=dev \
  java -jar sable-app/target/sable-app-0.0.1-SNAPSHOT.jar --server.port=18080

# 拿种子账号真实登录一次
curl -s --noproxy '*' -X POST http://localhost:18080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Sable@2026"}'
```

- **真实登录这一步不能省。** 种子里写的是 BCrypt 哈希，光看"数据插进去了"没有意义 ——
  只有登录返回 `code = 0` 才证明哈希与明文对得上、账号状态可用。
- 顺带确认**不带** `SPRING_PROFILES_ACTIVE=dev` 时同一个空库会停在 V1、零用户 ——
  这是"生产不会被塞进演示账号"的证据。
- 本机 `curl` 访问 localhost 必须加 `--noproxy '*'`（环境里设了 `HTTP_PROXY`，
  不加会走代理并报 `upstream connect failed`）。

### 5.3 验证多租户（改动 `db/tenant/` 或租户相关代码后必做）

```bash
# 一次性空库 + dev profile 起一次（dev 打开了 provision-on-startup）
createdb -h localhost -U <superuser> -O sable sable_tenant_check
DB_NAME=sable_tenant_check SPRING_PROFILES_ACTIVE=dev \
  java -jar sable-app/target/sable-app-0.0.1-SNAPSHOT.jar --server.port=18080

# 1) 两个 schema 都在，且表分居两处
psql -h localhost -U <superuser> -d sable_tenant_check -c '\dn'      # public / tenant_1
psql -h localhost -U <superuser> -d sable_tenant_check -c \
  "SELECT schemaname, count(*) FROM pg_tables
   WHERE schemaname IN ('public','tenant_1') GROUP BY 1;"
# 期望：public 9 张 + tenant_1 12 张（另加 flyway_schema_history 各一份）
# 2) public 里**不应**再有那 12 张业务表
psql -h localhost -U <superuser> -d sable_tenant_check -c \
  "SELECT to_regclass('public.data_source');"                        # 期望 NULL
# 3) 登录后调一个业务接口，走通即为路由正确
curl -s --noproxy '*' -X POST http://localhost:18080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Sable@2026"}'
```

- **第 2 步是最关键的一条**：只要 `public.data_source` 还在，就说明迁移路径又混了，
  租户请求会静默读到公共 schema 的那份数据 —— 表现是一切正常，实际没有隔离。
- 真实验证"缺租户即报错"：把某个业务接口的令牌里 `tid` 声明去掉（或直接用一个不带租户的
  令牌访问），期望得到业务错误码 `40104` 而不是 `500` 或空列表。
- 顺带确认**两个租户错误码没有混**：`GET /system/tenants/999999` 应得 `40101`（租户不存在），
  而"不带 `tid` 的令牌调需要租户的写接口"（如 `POST /system/roles`）应得 `40104`（缺上下文）。
  两者若返回同一个码、或 `GET /system/tenants/{id}` 对不存在的租户返回 `code:0` + `data:null`，
  说明又有人把判定写回了调用点 —— 见 §3.3 与 §3.8。

> **⚠️ 报错 `Unable to find a single main class from the following candidates [SableApplication 2, SableApplication]` 时**：
> 本仓库位于会被云盘（iCloud / OneDrive 等）同步的目录下，同步服务会在 `target/` 里生成 `Xxx 2.class`、`Xxx 3.class`
> 这类副本。它们不影响源码，但会让 `spring-boot-maven-plugin:repackage` 找到多个主类。
> **解法：不要 `-DskipTests package` 直接重打，先 `clean`** —— `./mvnw -q clean package -DskipTests`。
> 同理，若出现"类重复定义""找不到符号"等莫名称译错误，先怀疑 `target/` 里的副本，`clean` 一次即可。
> 排查命令：`find . -path "*/target/*" -name "* [0-9].class" | head`（不会有输出即为干净）。

### 5.4 验证 JSONB 写入（改动 JSONB 相关代码后必做）

JSONB 能不能写进去，取决于 **JDBC 层的参数类型**（见 3.5 第 2 条），而单元测试碰不到 JDBC ——
只跑单测会得到"全绿但接口 500"的假象，**这已经真实发生过一次**。必须打真实接口：

```bash
TOKEN=$(curl -s --noproxy '*' -X POST http://localhost:18080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Sable@2026"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["accessToken"])')

# ① public schema —— role.permissions
curl -s --noproxy '*' -X POST http://localhost:18080/system/roles \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"JSONB_PROBE","displayName":"JSONB探针"}'

# ② tenant schema —— alert_rule 的 config / notify_channels
curl -s --noproxy '*' -X POST http://localhost:18080/alert/rules \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"JSONB探针规则","ruleType":"THRESHOLD","config":"{\"target_level\":\"SPI\"}"}'
```

- **两个 schema 都要打**。参数类型是**数据源级**配置，只测一个无法区分"配置生效"与
  "某个接口碰巧写对了"。
- 落库后确认类型，别只看接口返回：
  `SELECT pg_typeof(permissions) FROM public.role WHERE name='JSONB_PROBE';` → 必须是 `jsonb`。
- 期望 `code=0`。若得到 `column "xxx" is of type jsonb but expression is of type character varying`，
  就是数据源 URL 少了 `stringtype=unspecified`。
- ⚠️ 该参数的代价：PG 改为**按目标列类型解析**传入的字符串，内容不合法时直接报
  `ERROR: invalid input syntax for type json`（比静默写脏数据好，但要求每条写入路径都保证
  内容是合法 JSON）。目前 `AlertController.createRule` 直接透传 `request.getConfig()` 不校验，
  传非法 JSON 会得到 `code=1 系统内部错误` —— 属已知待修项。

### 5.5 验证前端（改动 `sable-admin/` 后必做）

```bash
cd sable-admin

# 1. 类型检查 —— 用不了 `tsc` 报"@umijs/max 没有导出成员"时，先跑 setup 生成类型
npx max setup            # 生成 src/.umi/，@umijs/max 的类型声明全靠它
npx tsc --noEmit

# 2. 单元测试
npx vitest run

# 3. 生产构建
npx max build
```

**装依赖时的两个坑（本机必踩，与网络无关）**：

- 沙箱把 `NODE_OPTIONS` 指向了一个文件系统 shim（`node-language-shim.cjs`），npm 在 `reify`
  阶段的成千上万次 `rename` 都经 IPC 转发，最后必然超时失败（
  `CODEBUDDY_BROKER_OUTCOME_UNKNOWN: Broker request timed out`）。**报错长得像网络问题，其实不是** ——
  报错前依赖已经全下到缓存里了。用
  `env -u NODE_OPTIONS -u HTTP_PROXY -u HTTPS_PROXY npm install` 才能装上。
- `HTTP_PROXY` 会让 npm / curl 访问本机服务时走代理。访问 localhost 一律加 `--noproxy '*'`。

**联调起法**（两个都要起）：

一键脚本，推荐日常用这个（后端增量构建后后台跑、前端前台跑，Ctrl+C 一起停）：
`./scripts/dev-start.sh`（Windows：`.\scripts\dev-start.ps1`），详见脚本头部注释。

手工起法如下 —— 后端端口要和 `config/proxy.ts` 的 target 对上（现为 8080）：

```bash
# 后端：注意**不要**用 `mvnw -pl sable-app -am spring-boot:run`
# ⚠️ 这种「直接目标调用」会在 reactor 里的**每个**模块上都执行一遍，而 -pl ... -am
# 必然把根聚合模块与 9 个兄弟模块一起拉进 reactor —— 它们都没有主类，于是在第一个
# 模块就报 "Unable to find a suitable main class"。实测把根模块排除掉也不行
# （reactor 里还有 sable-common 等）。要真用 spring-boot:run，得先把兄弟模块
# `install` 进本地仓库、再单独对 sable-app 执行；日常不值得，直接用下面这条：
./mvnw -pl sable-app -am -Dmaven.test.skip=true package
SPRING_PROFILES_ACTIVE=dev java -jar sable-app/target/sable-app-0.0.1-SNAPSHOT.jar --server.port=8080

# 前端：Umi 4 默认 8000
cd sable-admin && npm run dev
```

验证"代理是否真的通了"，直接打前端端口而不是后端端口：

```bash
curl -s --noproxy '*' -X POST http://localhost:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Sable@2026"}'
```

返回 `code=0` 且带 `accessToken` 就说明 `/api` 前缀被正确剥掉并转发到了后端。
拿到令牌后再打 `http://localhost:8000/api/system/users/me`，确认 `roles` / `permissions` 有值。

### 5.6 CI（`.github/workflows/ci.yml`）

推 `master` 与所有 PR 会跑两个并行 job，**这是唯一会自动执行的验证**——本地漏跑的东西它不会替你补：

| job | 步骤 | 对应本地命令 |
|-----|------|-------------|
| 后端 | `./mvnw -B -ntp verify` → 断言 fat jar 内容 | 同名（`verify` 会额外打出 fat jar） |
| 前端 | `npm ci` → `npm run lint` → `npm test` → `npm run build` | 见 §5.5 |

几个刻意的取舍：

- **后端用 `verify` 而不是 `test`**，并且**额外拆开 jar 断言** `hibernate-validator` /
  `jakarta.validation-api` / `spring-security-core` 三个实现类在 `BOOT-INF/lib/` 里 ——
  这是 §2 那个「`provided` 只保证编译可见」的坑唯一的自动防线：少了它们，编译过、单测过、
  启动也正常，功能却静默失效。加新 starter 到库模块（`provided`）时，记得同步这条断言。
- **没有 `services: postgres`**：现有测试全是纯单测（无 `@SpringBootTest` / Testcontainers），
  不连库。将来加集成测试请优先用 Testcontainers（测试自带库），别往 workflow 里塞 service。
- **前端必须过 `tsc` 与 `build`**：`vitest` 只加载被测模块，路由配置错误、按需加载写错这类问题
  只有生产构建能发现。
- action 全部钉在 `@v4`（`checkout` / `setup-java` / `setup-node` / `upload-artifact`）。
  升级大版本前先确认 runner 镜像的 Node 运行时已跟上。

**CI 覆盖不到**（需要真库或真接口，仍按 §5.1–§5.4 手工验）：Flyway 迁移能否从空库建起来、
多租户 schema 建表与路由、JSONB 真实写入、OpenAPI 与前端 `services/` 的一致性。

---

## 6. 当前状态与禁区

本仓库处于**脚手架阶段**（详见 `Readme.md` 的「项目状态」）。因此：

- **只做被要求的改动**。不要"顺手"补全无关模块、不要重构没被点名的代码。
- 大量 `// TODO` 是**有意保留**的，不要一次性批量"实现"，除非被明确要求。
- 改动前先读目标文件现状，**改动后不要改变未涉及的行为**。

### 已知缺口（不要误当成"已实现"）

> 这几条都不影响编译、也不会让测试变红，只会在真实使用中暴露 —— 排查时优先怀疑这里。

- **鉴权目前只覆盖 `sable-system`，另外五个业务模块是空白**：`@PreAuthorize` 只挂在
  `me.kirara.sable.system.controller` 下的控制器（`users` / `roles` / `tenants` / `configs`）。
  `discovery` / `classify` / `audit` / `assessment` / `alert` / `report` 下的接口
  **对任何已登录用户开放** —— 它们大多是骨架，但接口确实已经存在。
  `ControllerAuthorizationTest` 现在只扫 `sable-system` 的控制器包，扩模块时把对应包加进去。
- **租户管理是全局能力，而角色是租户内的资产**：`/system/tenants` 的语义属平台级
  （`FR-6.1.4`），但持有 `system:manage` 的任何租户管理员都能创建、停用、删除别的租户。
  同理 `GET /system/roles` 没有按租户过滤，`RoleController.update/delete/setPermissions`
  也不校验角色归属。**这个洞用权限码补不上** —— 需要一个"平台级管理员"的身份概念
  （不属于任何租户，或带一个租户之外的标记），是一次独立的设计决策。
  当前状态仍严格于改造前（以前是**任何登录用户**都能做这些事）。
- **数据范围权限（`FR-6.2.3`）零实现**：`tenant_user.department` 只存不读，
  用户能看到本租户的**全部**数据，而需求要求"只能看到本部门/本业务线的数据"。
- **RLS 是空转**：`tenant_{id}` 下的表虽然 `ENABLE ROW LEVEL SECURITY`，但没有 POLICY、
  对表属主也不生效。**它不是隔离手段**，真正的隔离靠 schema 路由（§3.8）。
- **CI 只跑单测与构建，跑不到"要真库"的东西**：Flyway 从空库建全量、多租户 schema 路由、
  JSONB 真实写入，这几样在流水线里都是绿的也说明不了问题（见 §5.1–§5.4）。
- **结构化 JSON 日志未做**；集中日志平台与访问日志也未做。
- `report` / `report_schedule` 两张表缺失（`sable-report` 已有实体与 Mapper）。
- `audit_log` 的分区只补了"下个月"，且**无分区时写入会直接失败**（权宜之计）。
- 租户被删除时，它的连接池不会被关闭。

---

## 7. 提交前自检清单

- [ ] 没有引入新依赖 / 新技术栈
- [ ] 新增包都有 `package-info.java`（中文）
- [ ] DTO / 实体为手写 getter/setter，未用 Lombok
- [ ] Controller 返回 `R<T>`，异常走 `BusinessException`
- [ ] 新错误码落在本模块号段且为中文
- [ ] 没有"借码"：同一个语义只有一处判定（如租户上下文缺失统一走 `TenantContext.requireTenantId()`）
- [ ] 表结构变更已**新增**迁移脚本（未改动任何已有迁移文件）并同步数据库设计文档
- [ ] 表结构变更写在了**正确的那条迁移路径**上（公共表 `db/migration/`、业务表 `db/tenant/`），
      且租户脚本里没有 schema 前缀
- [ ] 没有把演示账号一类的假数据写进 `db/migration/` 与 `db/tenant/`（假数据只允许出现在 `db/dev/`）
- [ ] 业务模块的查询都带得上租户上下文；没关掉 `sable.tenant.fail-fast-without-tenant`
- [ ] 新增的接口挂了 `@PreAuthorize`，且权限码经 `T(...Permission).XXX` 引用字典（见 §3.10）
- [ ] 没有顺手启用 `.cors()`、没有改 `spring.security.filter.order`（见 §3.10 装配禁区）
- [ ] 改了 JSONB 相关代码时，已按 §5.4 打**真实接口**验证（只跑单测不算 —— 那个坑踩过）
- [ ] 日志走 SLF4J，没有 `System.out`，没有给 mybatis-plus 配 `log-impl`
- [ ] `./mvnw -q -DskipTests compile` 通过
- [ ] 改动都过得了 CI（`./mvnw -B verify` + 前端的 lint / test / build，见 §5.6）；
      本地跑不起来时别只跑编译就提交 —— 绿的那一步正是 CI 的全部内容
- [ ] 改了 `scripts/dev-start.sh` 时，`scripts/dev-start.ps1` 已同步改（两版功能对等；
      ps1 必须存成 **UTF-8 with BOM**，否则 PowerShell 5.1 按 GBK 读会乱码）
- [ ] 没有改动与本任务无关的文件

**动了 `sable-admin/` 时额外确认：**

- [ ] `npx tsc --noEmit`、`npx vitest run`、`npx max build` 三项都过（见 §5.5）
- [ ] 新增接口调用走 `/api` 前缀，且判断成败看 `R.code` 而不是 HTTP 状态（见 §3.9）
- [ ] 没有向第三方域名引资源（字体 / 图片 / 埋点）—— 合规产品页面不应发起这类请求
- [ ] 没有把 `src/access.ts` 的权限判断当成安全边界使用

---

## 8. 文档权威顺序

出现分歧时，按此优先级判断"正确答案"：

1. 现有代码与 `db/migration/` 下的迁移脚本（已落地的事实）
2. `doc/数据合规系统-系统架构设计文档.md` / `-数据库设计文档.md` / `-API接口设计文档.md`
3. `doc/数据合规系统-需求分析报告.md` / `-分析报告.md`（背景与规划）
4. 本文件（协作约定）
5. 你的推测（**最不可信，能不猜就不猜，拿不准就问**）
