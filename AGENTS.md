# AGENTS.md

> 本文件是 AI 编程助手在本仓库工作的**强制约定**。开始任何改动前，请完整读一遍。
>
> 目标只有一个：让 AI 的产出**与既有工程保持一致**，而不是自由发挥、另起炉灶。
> 当你的"创意"与本文件冲突时，**以本文件为准**。

---

## 0. 铁律速览（TL;DR）

1. **不引入新依赖**。尤其禁止擅自加入 Lombok、MapStruct、Spring Security、JWT 库、规则引擎（Drools 等）、前端框架。确需新增，先提出来，等确认。
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
- 隔离靠**连接自身的 `currentSchema`**（`TenantRoutingDataSource` + 每租户一个连接池），
  不靠会话级 `search_path`。设计文档 §5.2 给出的
  `jdbcTemplate.execute("SET search_path TO ...")` 样例**在本项目中是错的**：那条连接多半不是
  MyBatis 随后执行 SQL 用的那一条，还容易污染池里连接状态（串租户的来源）——**不要照抄**。
- 连接池**按租户分开**，改 `sable.tenant.pool-max-size` 前先估
  「租户数 × 本值 ≤ 数据库 `max_connections`」。

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

---

## 6. 当前状态与禁区

本仓库处于**脚手架阶段**（详见 `Readme.md` 的「项目状态」）。因此：

- **只做被要求的改动**。不要"顺手"补全无关模块、不要重构没被点名的代码。
- 大量 `// TODO` 是**有意保留**的，不要一次性批量"实现"，除非被明确要求。
- 改动前先读目标文件现状，**改动后不要改变未涉及的行为**。

---

## 7. 提交前自检清单

- [ ] 没有引入新依赖 / 新技术栈
- [ ] 新增包都有 `package-info.java`（中文）
- [ ] DTO / 实体为手写 getter/setter，未用 Lombok
- [ ] Controller 返回 `R<T>`，异常走 `BusinessException`
- [ ] 新错误码落在本模块号段且为中文
- [ ] 表结构变更已**新增**迁移脚本（未改动任何已有迁移文件）并同步数据库设计文档
- [ ] 表结构变更写在了**正确的那条迁移路径**上（公共表 `db/migration/`、业务表 `db/tenant/`），
      且租户脚本里没有 schema 前缀
- [ ] 没有把演示账号一类的假数据写进 `db/migration/` 与 `db/tenant/`（假数据只允许出现在 `db/dev/`）
- [ ] 业务模块的查询都带得上租户上下文；没关掉 `sable.tenant.fail-fast-without-tenant`
- [ ] 改了 JSONB 相关代码时，已按 §5.4 打**真实接口**验证（只跑单测不算 —— 那个坑踩过）
- [ ] 日志走 SLF4J，没有 `System.out`，没有给 mybatis-plus 配 `log-impl`
- [ ] `./mvnw -q -DskipTests compile` 通过
- [ ] 没有改动与本任务无关的文件

---

## 8. 文档权威顺序

出现分歧时，按此优先级判断"正确答案"：

1. 现有代码与 `db/migration/` 下的迁移脚本（已落地的事实）
2. `doc/数据合规系统-系统架构设计文档.md` / `-数据库设计文档.md` / `-API接口设计文档.md`
3. `doc/数据合规系统-需求分析报告.md` / `-分析报告.md`（背景与规划）
4. 本文件（协作约定）
5. 你的推测（**最不可信，能不猜就不猜，拿不准就问**）
