# AGENTS.md

> 本文件是 AI 编程助手在本仓库工作的**强制约定**。开始任何改动前，请完整读一遍。
>
> 目标只有一个：让 AI 的产出**与既有工程保持一致**，而不是自由发挥、另起炉灶。
> 当你的"创意"与本文件冲突时，**以本文件为准**。

---

## 0. 铁律速览（TL;DR）

1. **不引入新依赖**。尤其禁止擅自加入 Lombok、MapStruct、Spring Security、JWT 库、规则引擎（Drools 等）、前端框架。确需新增，先提出来，等确认。
2. **不引入新技术栈**。语言、框架、数据库以第 1 节表格为准，不得替换或"升级"。
3. **不臆造**。不得编造不存在的表、字段、接口、错误码。一切以 `doc/`、`sable-app/src/main/resources/db/schema.sql` 和现有代码为准。
4. **不重写风格**。沿用现有分层、命名、注释习惯（详见第 3 节）。不要"顺手美化"未提及的文件。
5. **不改公共契约**。`sable-common` 中的 `R`、`ErrorCode`、`BaseEntity`、`PageQuery`/`PageResult` 等为全局约定，不得随意改动其结构或语义。
6. **不新增模块**。模块划分是设计决策，需要新模块时先讨论。
7. **数据库变更必须同步文档**。改表结构必须同时更新 `schema.sql` 与 `doc/数据合规系统-数据库设计文档.md`。
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
| `sable-app` | 启动入口、`@Configuration`、Web 层 Filter、`application.yml`、`schema.sql` | 业务领域逻辑 |
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
| 表 / 列 | snake_case，`created_at` 等 | 见 `schema.sql` |

### 3.3 统一响应与异常

- Controller 一律返回 `R<T>`：`R.ok()` / `R.ok(data)`，失败用 `R.fail(...)`。
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

### 3.6 注释

- 类、public 方法使用 **中文 Javadoc**；段落用 `<p>`。
- 遇到确实没实现的地方，保留 `// TODO 说明` 的既有写法（全项目统一用 `TODO` 中文描述），**不要**改成 `FIXME`、`XXX` 或英文。

---

## 4. 标准任务配方

**新增一个 REST 接口**
1. 在对应模块 `dto/` 加请求 / 响应类（手写 getter/setter）。
2. 在 `service/` 加接口方法，`service/impl/` 实现。
3. 在 `controller/` 加方法，返回 `R<T>`，`@Valid` 校验入参。
4. 若新增错误分支，在 `ErrorCode` 本模块号段加码。

**新增一张表 / 一个实体**
1. 改 `sable-app/src/main/resources/db/schema.sql`（含约束、索引、`COMMENT` 风格与现有一致）。
2. 同步 `doc/数据合规系统-数据库设计文档.md`。
3. 建 `entity/`（继承 `BaseEntity` 视情况）、`mapper/`、`service/`、`service/impl/`。

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

- 运行需要 PostgreSQL（连接参数见 `Readme.md` 的环境变量表）。
- 当前仅有 `sable-app` 引入了 `spring-boot-starter-test`；若要给其他模块加测试，需先在该模块 `pom.xml` 声明 test 依赖。

> **⚠️ 报错 `Unable to find a single main class from the following candidates [SableApplication 2, SableApplication]` 时**：
> 本仓库位于会被云盘（iCloud / OneDrive 等）同步的目录下，同步服务会在 `target/` 里生成 `Xxx 2.class`、`Xxx 3.class`
> 这类副本。它们不影响源码，但会让 `spring-boot-maven-plugin:repackage` 找到多个主类。
> **解法：不要 `-DskipTests package` 直接重打，先 `clean`** —— `./mvnw -q clean package -DskipTests`。
> 同理，若出现"类重复定义""找不到符号"等莫名称译错误，先怀疑 `target/` 里的副本，`clean` 一次即可。
> 排查命令：`find . -path "*/target/*" -name "* [0-9].class" | head`（不会有输出即为干净）。

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
- [ ] 表结构变更已同步 `schema.sql` 与数据库设计文档
- [ ] `./mvnw -q -DskipTests compile` 通过
- [ ] 没有改动与本任务无关的文件

---

## 8. 文档权威顺序

出现分歧时，按此优先级判断"正确答案"：

1. 现有代码与 `schema.sql`（已落地的事实）
2. `doc/数据合规系统-系统架构设计文档.md` / `-数据库设计文档.md` / `-API接口设计文档.md`
3. `doc/数据合规系统-需求分析报告.md` / `-分析报告.md`（背景与规划）
4. 本文件（协作约定）
5. 你的推测（**最不可信，能不猜就不猜，拿不准就问**）
