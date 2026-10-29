# 数据合规管理系统 — API 接口设计文档

> 文档编号：SABLE-API-2026-001
> 版本：v1.0
> 编制日期：2026-09-08
> 基础路径：`/api/v1`

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | 2026-09-08 | 初始版本 |
| v1.1 | 2026-10-14 | **成功码由本文档误写的 `200` 更正为 `0`**（与 `R.ok()` 实现一致），全文 27 处示例同步；1.2 增加"业务码 vs HTTP 状态码"判读说明；1.3 错误码表按 `sable-common` 的 `ErrorCode` 枚举逐条校准 |

---

## 一、接口规范

### 1.1 通用约定

| 约定 | 说明 |
|------|------|
| 协议 | HTTPS |
| 数据格式 | JSON (`Content-Type: application/json`) |
| 字符编码 | UTF-8 |
| 认证方式 | Bearer Token (JWT) |
| 时间格式 | ISO 8601 (`2026-09-08T10:30:00+08:00`) |
| 日期格式 | `yyyy-MM-dd` |
| 分页参数 | `page` (从1开始), `size` (默认20, 最大100) |
| 排序参数 | `sort=field:asc,field2:desc` |
| ID类型 | BIGINT，前端使用字符串传输避免精度丢失 |

### 1.2 统一响应结构

> **`code` 是业务码，与 HTTP 状态码是两个维度。成功固定为 `0`，不是 `200`。**
> 客户端判断成功一律用 `code === 0`；`200` 是传输层的值，不进入本字段。理由见 1.3。

**成功响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "timestamp": "2026-09-08T10:30:00.123+08:00",
  "traceId": "abc123def456"
}
```

**分页响应：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [ ],
    "total": 156,
    "page": 1,
    "size": 20,
    "pages": 8
  },
  "timestamp": "2026-09-08T10:30:00.123+08:00"
}
```

**错误响应：**

```json
{
  "code": 40001,
  "message": "数据源连接失败：超时",
  "data": null,
  "timestamp": "2026-09-08T10:30:00.123+08:00",
  "traceId": "abc123def456"
}
```

### 1.3 错误码规范

**为什么成功码是 `0` 而不是 `200`：** `code` 是**业务码**，与 HTTP 状态码分属两个维度。
`200` 表达的是传输层"请求已被成功处理"，把它抄进 body 只会让两个维度重叠；而 `0` 表示成功是
跨领域的通用约定 —— gRPC canonical code 的 `OK = 0`（官方文档同时标注其 HTTP 映射为 `200 OK`，
正说明二者是两回事）、C 的 `errno`、进程退出码、Windows `ERROR_SUCCESS` 均为 0。
服务端实现见 `sable-common` 的 `R.ok()` 与 `ErrorCode.SUCCESS`。

| 范围 | 模块 | 说明 |
|------|------|------|
| 0 | 全局 | **成功** |
| 1-2 | 全局 | 框架级错误：内部错误、参数校验失败 |
| 404 / 405 / 415 | 全局 | 与 HTTP 状态码同值：路径不存在 / 方法不支持 / 媒体类型不支持 |
| 40001-40099 | 认证鉴权 | 登录、Token、MFA、账号锁定 |
| 40101-40199 | 租户管理 | 租户相关错误、租户上下文缺失 |
| 40201-40299 | 数据源管理 | 数据源连接、扫描相关 |
| 40301-40399 | 分类分级 | 规则、分级相关 |
| 40401-40499 | 审计引擎 | 日志查询、SDK 相关 |
| 40501-40599 | 合规评估 | 评估、检查项相关 |
| 40601-40699 | 告警引擎 | 规则、事件相关 |
| 40701-40799 | 报告引擎 | 报告生成相关 |
| 40801-40899 | 权限角色 | 无权限、角色相关 |
| 40901-40999 | 文件管理 | 文件上传、大小限制相关 |

> `404` / `405` / `415` 三个值与 HTTP 状态码同值是**冗余**的：这几种情况下 HTTP 状态码本身
> 就是真实的，客户端看 HTTP 状态即可。保留在 body 里只是为了让"只解析 body"的调用方能统一分支。

**常用错误码**（下表与 `sable-common` 的 `ErrorCode` 枚举逐条对应，改代码须同步改表）：

| 错误码 | 含义 |
|--------|------|
| 0 | 成功 |
| 1 | 系统内部错误 |
| 2 | 参数校验失败 |
| 40001 | 认证失败 |
| 40002 | Token 已过期 |
| 40003 | Token 无效 |
| 40004 | 需要 MFA 验证 |
| 40005 | MFA 验证码错误 |
| 40006 | 账号已锁定 |
| 40101 | 租户不存在 |
| 40102 | 租户已停用 |
| 40103 | 租户配额超限 |
| 40104 | 缺少租户上下文（服务端缺陷，不是用户输入问题） |
| 40201 | 数据源不存在 |
| 40202 | 数据源连接失败 |
| 40203 | 数据源名称重复 |
| 40301 | 分类规则不存在 |
| 40302 | 规则冲突 |
| 40401 | 审计事件格式错误 |
| 40501 | 评估任务不存在 |
| 40502 | 评估任务正在执行中 |
| 40601 | 告警规则不存在 |
| 40701 | 报告不存在 |
| 40702 | 报告生成失败 |
| 40801 | 无权限访问 |
| 40802 | 角色不存在 |
| 40901 | 文件不存在 |
| 40902 | 文件大小超限 |

> **`40101` 与 `40104` 是两件事，不要合并。**
> `40104` 表达「请求没带租户上下文」——**调用姿势**问题（链路漏了租户，或客户端既没带
> `X-Tenant-Id`、令牌里也没有 `tid`），属服务端缺陷；`40101` 表达「要找的那个租户不存在」
> ——**请求内容**问题，前端应提示用户。类比 HTTP：前者像 `401`（没带凭证），后者像 `404`
> （资源不存在）。服务端 `40104` 的唯一出口是 `TenantContext.requireTenantId()`。

### 1.4 认证机制

**请求头：**

| 请求头 | 必需 | 说明 |
|--------|------|------|
| `Authorization` | 是 | `Bearer <accessToken>` |
| `X-Tenant-Id` | 否 | 租户以令牌中的 `tid` 为准；仅当令牌未携带租户（系统级用户）时本请求头才生效，用于超级管理员跨租户操作 |
| `X-Request-Id` | 否 | 客户端生成的请求追踪 ID |

**免认证路径（白名单）：**

| 路径 | 放行原因 |
|------|---------|
| `/auth/login` | 还没有令牌 |
| `/auth/refresh` | 访问令牌已过期，只能用刷新令牌换新 |
| `/auth/logout` | 按提交的令牌做吊销，令牌无效时是空操作；若要求先认证，客户端在令牌过期后将无法登出 |
| `/error`、`/favicon.ico` | 容器内部转发与浏览器默认请求 |

除白名单外**所有**路径都必须携带有效的访问令牌，白名单由 `sable.auth.whitelist` 配置。

**认证失败响应：** HTTP `401` + 统一响应体（结构见 1.2 节），`code` 取值：

| code | 场景 |
|------|------|
| 40002 | 令牌已过期 |
| 40003 | 缺少令牌 / 令牌非法 / 令牌类型不对（例如拿刷新令牌访问业务接口）/ 令牌已被吊销 |

**鉴权失败响应：** HTTP `403` + 统一响应体，`code` 固定 `40801`。

通过认证只说明"你是谁"，能否调某个接口还要看令牌里的 `perm` 是否包含该接口要求的权限码。
校验由 `@PreAuthorize` 在 Controller 方法上完成，权限码字典是
`me.kirara.sable.common.Permission`。这里按 `403` 返回而不是"200 + 非 0 code"：
调用方用错了入口，不是"服务端受理了但业务上不成功"。

**各接口需要的权限（当前已绑定的范围）：**

| 接口 | 要求 |
|------|------|
| `/auth/login`、`/auth/refresh`、`/auth/logout` | 白名单，无需令牌 |
| `PUT /auth/password` | 仅需已认证（改自己的密码不该要管理员权限） |
| `GET /system/users/me` | 仅需已认证 |
| `/system/users/**`（除 me）、`/system/roles/**`、`/system/tenants/**`、`/system/configs`、`/system/health` | `system:manage` |
| 其余业务模块（discovery / audit / assessment / alert / report） | **尚未绑定**，见 §6 的已知缺口 |

> ⚠️ **权限的生效时机**：权限放在令牌里，所以改角色或改权限后，最长要等访问令牌过期
> （`sable.auth.access-token-ttl`，默认 30 分钟）才在接口校验上生效。需要立即生效时，
> 吊销该用户的令牌（登出走的正是这条路）。
> 另外注意 `GET /system/users/me` 是按**数据库当前状态**装配的 —— 因此"菜单立刻出现、
> 点进去仍 403"会在刚授权后的一小段时间内出现。这是刻意的取舍（见
> `AuthServiceImpl#issueTokens` 的说明），不是缺陷。

**Token 结构 (JWT Payload)：**

```json
{
  "iss": "sable",
  "sub": "42",
  "jti": "34206cf8-8029-4743-a85c-eed2ab3c9034",
  "tid": 1001,
  "usr": "admin",
  "roles": ["SUPER_ADMIN"],
  "perm": ["data_source:read", "data_source:write"],
  "typ": "access",
  "iat": 1725768600,
  "exp": 1725770400
}
```

| 声明 | 含义 |
|------|------|
| `sub` | 用户 ID（字符串） |
| `jti` | 令牌唯一标识；登出吊销按它定位单枚令牌，不能用 `iat` 代替（秒级精度无法区分同秒签发的令牌） |
| `tid` | 租户 ID；系统级用户为空 |
| `usr` | 用户名 |
| `roles` | 角色标识列表（取角色的 `name`） |
| `perm` | 权限标识列表 —— 该用户全部角色的 `permissions` 并集去重。**接口级鉴权的判据**，由 `@PreAuthorize` 读取；权限码字典见 `me.kirara.sable.common.Permission` |
| `typ` | 令牌类型：`access` / `refresh`。刷新令牌只带 `sub`、`jti`、`typ`，不含业务声明 |

**租户如何确定：** 以令牌中的 `tid` 为准。`tenant_user` 允许一个用户同时属于多个租户，
而登录接口没有租户参数，因此当前在登录 / 刷新时取"最早加入"的那一个写入令牌
（多租户选择与切换尚未设计）。

---

## 二、认证接口

### 2.1 用户登录

```
POST /auth/login
```

**请求体：**

```json
{
  "username": "admin",
  "password": "P@ssw0rd123",
  "mfaCode": "123456"       // 可选，启用MFA时必填
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 1800,
    "mfaRequired": false
  }
}
```

> ⚠️ **响应体里没有 `user`，也没有 `tokenType`**（2026-10-14 按代码校正）。
> 后端 `LoginResponse` 只有上面这四个字段：
>
> - 原文档样例中的 `user` 对象不存在 —— **登录后必须再调一次 `GET /system/users/me`**
>   才能拿到用户信息与角色权限。前端的安全做法是登录成功后立刻拉取，不要依赖登录响应。
> - `tokenType` 也不存在。令牌类型恒为 `Bearer`，需要时按固定值拼 `Authorization` 头即可。
> - `mfaRequired` 目前**恒为 `false`**：后端对已启用 MFA 的账号直接返回错误码 `40004`
>   （TOTP 校验尚未实现），走不到"返回 true 让前端再提交一次验证码"这条路。
>   保留该字段是为了将来接入 TOTP 时无需改响应结构。
> - 令牌类型声明是 `Bearer`（见 §1.4 认证机制）。

### 2.2 刷新Token

```
POST /auth/refresh
```

**请求体：**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**响应：** 同登录响应结构（`accessToken` / `refreshToken` / `expiresIn` / `mfaRequired`）。

> ⚠️ **刷新会签发一对全新令牌**：每次调用都返回**新的** `accessToken` 与 `refreshToken`，
> 返回的 `refreshToken` 与传入的不是同一个。前端拿到响应后应**整体覆盖**本地保存的两个令牌，
> 避免本地那份与最近一次签发的不一致。
>
> ⚠️ **但传入的 `refreshToken` 并不会因此作废**（2026-10-14 实测确认）。`AuthServiceImpl.refresh()`
> 只做校验 —— 有效期、令牌类型、用户是否存在、是否已被吊销、账号是否可用 —— 然后签发新的一对，
> **不吊销传入的那一个**；它在本来的有效期内（7 天）继续可用。实测把同一个 `refreshToken` 连刷两次，
> 两次都返回 `code=0`。
>
> 这条对前端有直接影响：**并发刷新不会因为"令牌已轮换"而失败**，所以不要据此推断
> "多个请求同时续期会导致用户被踢下线"。客户端把并发刷新收敛成一次的理由是另外两条 ——
> 避免重复签发 N-1 对令牌、以及避免"本地最终存的是哪一个响应"变得不确定。
> 真正会刷新失败的情形只有三种：刷新令牌已过期、已被登出吊销、账号被停用或锁定。

### 2.3 退出登录

```
POST /auth/logout
```

**请求头：** `Authorization: Bearer <accessToken>`（被吊销的访问令牌）

**请求体：** 可选

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."   // 可选，一并吊销刷新令牌
}
```

**响应：**

```json
{
  "code": 0,
  "message": "success"
}
```

**说明：**
- 本接口在**免认证白名单**内：没有令牌、令牌已过期或非法时同样返回 `success`，
  不暴露令牌状态；客户端可在令牌失效后照常调用以清理会话。
- 服务端按令牌的 `jti` 写入吊销表，此后该令牌一律判为无效。
- 刷新令牌不在请求头中，若客户端希望彻底登出，应在请求体携带 `refreshToken` 一并吊销；
  否则刷新令牌在有效期内仍可换取新的访问令牌。

### 2.4 修改密码

```
PUT /auth/password
```

**请求体：**

```json
{
  "oldPassword": "OldP@ss123",
  "newPassword": "NewP@ss456"
}
```

**校验规则：**
- 最少8位
- 包含大写字母、小写字母、数字
- 不能与最近3次密码相同

---

## 三、租户管理接口

### 3.1 创建租户

```
POST /system/tenants
```

**权限：** `system:manage`

**请求体：**

```json
{
  "name": "示例科技有限公司",
  "code": "example_tech",
  "planType": "PROFESSIONAL",
  "contactName": "张三",
  "contactEmail": "zhangsan@example.com",
  "contactPhone": "13800138000",
  "config": {
    "maxDataSources": 20,
    "maxUsers": 50,
    "storageQuotaGb": 100,
    "auditLogRetentionDays": 180
  }
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 1001,
    "name": "示例科技有限公司",
    "code": "example_tech",
    "planType": "PROFESSIONAL",
    "status": "ACTIVE",
    "createdAt": "2026-09-08T10:30:00+08:00"
  }
}
```

### 3.2 查询租户列表

```
GET /system/tenants?page=1&size=20&status=ACTIVE&keyword=科技
```

**响应：** 标准分页结构，records 为租户对象数组。

### 3.3 查询租户详情

```
GET /system/tenants/{id}
```

租户不存在（或已软删）时返回 `40101`，**不是** `code:0` + `data:null`。

### 3.4 更新租户

```
PUT /system/tenants/{id}
```

**请求体：** 同创建，所有字段可选。

### 3.5 停用/启用租户

```
PUT /system/tenants/{id}/status
```

**请求体：**

```json
{
  "status": "SUSPENDED"
}
```

### 3.6 查询租户用量

```
GET /system/tenants/{id}/usage
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "dataSourceCount": 8,
    "maxDataSources": 20,
    "userCount": 15,
    "maxUsers": 50,
    "storageUsedGb": 23.5,
    "storageQuotaGb": 100,
    "auditLogCount": 1250000,
    "auditLogRetentionDays": 180
  }
}
```

---

## 四、用户与权限接口

### 4.1 创建用户

```
POST /system/users
```

**请求体：**

```json
{
  "username": "zhangsan",
  "password": "InitP@ss123",
  "email": "zhangsan@example.com",
  "phone": "13800138000",
  "displayName": "张三",
  "department": "技术部",
  "roleIds": [1, 2]
}
```

> **租户来源**：请求体里不传租户，新用户归属到调用方访问令牌中的租户（`tid`）。
> **`department`** 落 `tenant_user.department`（部门是「用户在某租户下的」属性，不是 `sys_user` 的列）。
> **`roleIds`** 逐个写入 `tenant_user_role`，一个用户可同时持有多个角色；不传或传空即为无角色。
> 用户、租户归属、角色授予在同一事务内完成，任一步失败都不会留下「账号建了却进不了租户」的半成品。

### 4.2 查询用户列表

```
GET /system/users?page=1&size=20&keyword=zhang&status=ACTIVE
```

### 4.3 更新用户

```
PUT /system/users/{id}
```

### 4.4 删除用户（软删除）

```
DELETE /system/users/{id}
```

### 4.5 分配角色

```
PUT /system/users/{id}/roles
```

**请求体：**

```json
{
  "roleIds": [1, 2]
}
```

**覆盖式语义**：`roleIds` 即该用户在当前租户下的**最终**角色集合 —— 未列入的角色会被收回，
传空数组等于收回其全部角色。租户取自访问令牌，与创建用户一致。

### 4.6 重置密码

```
POST /system/users/{id}/password-reset
```

### 4.7 锁定/解锁用户

```
PUT /system/users/{id}/status
```

**请求体：**

```json
{
  "status": "LOCKED",
  "lockedUntil": "2026-09-09T10:00:00+08:00"
}
```

### 4.8 角色管理

```
GET    /system/roles                    ← 查询角色列表
POST   /system/roles                    ← 创建角色
PUT    /system/roles/{id}               ← 更新角色
DELETE /system/roles/{id}               ← 删除角色（系统角色不可删）
GET    /system/roles/{id}/permissions   ← 查询角色权限
PUT    /system/roles/{id}/permissions   ← 设置角色权限
```

**设置权限请求体：**

```json
{
  "permissions": [
    "data_source:read",
    "data_source:write",
    "audit_log:read",
    "audit_log:export",
    "assessment:read",
    "assessment:write",
    "report:read",
    "report:export"
  ]
}
```

### 4.9 获取当前用户信息

```
GET /system/users/me
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 42,
    "username": "alice",
    "displayName": "系统管理员",
    "email": "alice@sable.dev",
    "phone": "13800138000",
    "status": "ACTIVE",
    "roles": ["SUPER_ADMIN", "COMPLIANCE_OFFICER"],
    "permissions": ["system:manage", "audit_log:read"],
    "mfaEnabled": false,
    "lastLoginAt": "2026-10-14T09:12:31.482001",
    "createdAt": "2026-09-08T09:00:00.000000",
    "updatedAt": "2026-10-14T09:12:31.482001"
  }
}
```

> ⚠️ 2026-10-14 按代码校正的三处（原样例均与实现不符）：
>
> - **`roles` 是字符串数组，不是对象数组**。取的是角色 `name`（如 `SUPER_ADMIN`），
>   不带 `id` / `displayName`。要展示中文名需另外查角色列表。
> - **`permissions` 是权限标识字符串数组**，且**当前没有权限码字典** —— 值就是角色上
>   配置的自由字符串（见 `role.permissions`）。上例只是示例取值，不要当作合法枚举。
> - **`department` 不在此投影里**。它落在 `tenant_user.department`（创建用户时会写入），
>   但 `UserResponse` 未包含该字段，所以响应里看不到。
> - 空值字段（如 `avatarUrl`、`lockedUntil`）会**整个从 JSON 里消失**，而不是返回 `null` ——
>   全局序列化配了 `default-property-inclusion: non_null`。`roles` / `permissions` 因此
>   刻意用**空数组**而不是 null 作默认值，保证前端可以无脑遍历。
> - 时间格式是 `yyyy-MM-ddTHH:mm:ss.SSSSSS`（带 `T`、微秒 6 位、**无时区**），
>   不是原样例的 `+08:00` 形式。`R.timestamp` 则是 epoch 毫秒。

**关于用户相关接口的响应（已实现约定）：**

- 4.1 / 4.2 / 4.9 一律返回**脱敏投影**，字段白名单为：
  `id` / `username` / `displayName` / `email` / `phone` / `avatarUrl` / `status` /
  `mfaEnabled` / `lastLoginAt` / `lockedUntil` / `createdAt` / `updatedAt` / `roles` / `permissions`。
- `passwordHash`、`mfaSecret` 属凭证，**任何接口都不会返回**。除投影本身外，启动模块还挂了一层
  Jackson 兜底规则：即使有人直接序列化 `sys_user` 实体（或把它嵌进别的响应对象），这两个字段同样不会出网。
- `lastLoginIp`、`loginFailCount`、`passwordChangedAt`、`deletedAt` 属内部状态，一并不返回。

> **`roles` / `permissions` 的装配范围（重要）**：这两个字段**只在 `GET /system/users/me`
> 上装配** —— 它按当前登录用户的租户查出全部角色并做权限并集去重。4.1（用户列表）与
> 4.2（用户详情）走的是同一个 `UserResponse`，但**不装配**这两项，因此响应里恒为 `[]`。
> 管理端的用户列表页若需要展示某人的角色，得单独取角色列表再对照，**不能指望列表接口带出来**。


---

## 五、数据源管理接口

### 5.1 添加数据源

```
POST /discovery/sources
```

**请求体：**

```json
{
  "name": "生产库-用户中心",
  "type": "MYSQL",
  "host": "192.168.1.100",
  "port": 3306,
  "databaseName": "user_center",
  "username": "readonly_user",
  "password": "encrypted_password",
  "jdbcOptions": "useSSL=true&serverTimezone=Asia/Shanghai",
  "groupName": "生产环境",
  "description": "用户中心主数据库"
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 101,
    "name": "生产库-用户中心",
    "type": "MYSQL",
    "host": "192.168.1.100",
    "port": 3306,
    "databaseName": "user_center",
    "groupName": "生产环境",
    "status": "INACTIVE",
    "lastScanAt": null,
    "createdAt": "2026-09-08T10:30:00+08:00"
  }
}
```

### 5.2 查询数据源列表

```
GET /discovery/sources?page=1&size=20&type=MYSQL&status=ACTIVE&groupName=生产环境&keyword=用户
```

### 5.3 查询数据源详情

```
GET /discovery/sources/{id}
```

### 5.4 更新数据源

```
PUT /discovery/sources/{id}
```

### 5.5 删除数据源

```
DELETE /discovery/sources/{id}
```

> 级联删除关联的Schema、Table、Column数据。

### 5.6 测试连接

```
POST /discovery/sources/{id}/test
```

用库里存的口令解密后**真正连一次**目标库，超时约 5 秒。需要 `data_source:write` 权限
（这个动作会用凭证访问外部系统）。

**响应：**

```json
{
  "code": 0,
  "data": {
    "success": true,
    "message": "连接成功",
    "serverVersion": "PostgreSQL 16.15",
    "durationMs": 45
  }
}
```

**语义：**

- **连接失败不是接口错误**：仍返回 `code=0`，由 `success=false` + `message` 表达原因
  （客户端要展示的是"连不上 + 为什么"，不是一个异常弹窗）。数据源不存在才是错误 40201。
- 探测成功把数据源 `status` 写成 `ACTIVE`，失败写成 `ERROR`。
- 当前支持 **PostgreSQL / MySQL**（两者都内置了驱动与方言）。
  Oracle / SQL Server 会返回"暂不支持 XXX 类型的连接（当前支持 MYSQL / POSTGRESQL）"，
  MongoDB / Redis 同样不支持；**这种情况不改数据源状态**
  （环境缺能力不该把租户的数据源标成异常）。
- MySQL 常见连接参数（写进 `jdbcOptions`）：自建库/无 TLS 时用
  `useSSL=false&allowPublicKeyRetrieval=true`（caching_sha2_password 首次认证需要取公钥）；
  云数据库按厂商要求配 `useSSL=true` 与证书参数。
- `message` 与 `serverVersion` 不含口令，也不回显完整连接串。

### 5.7 数据源分组管理

分组是 `data_source.group_name` 的**派生**概念：没有数据源就没有分组，也没有独立的分组表。

#### 5.7.1 查询分组列表

```
GET /discovery/sources/groups
```

**响应：** 分组名数组，按分组顺序排列（顺序落在 `data_source.group_sort` 上，
同一分组的所有行同值；未填分组的数据源不出现在这里）。

```json
{
  "code": 0,
  "data": ["生产环境", "测试环境", "开发环境"]
}
```

#### 5.7.2 调整分组顺序

```
PUT /discovery/sources/groups/reorder
```

**请求体：** 分组名数组，按期望的显示顺序排列。

```json
["测试环境", "生产环境"]
```

**语义：**

- 数组里的分组按顺序排在前面；**未出现在数组里的分组保持原有相对顺序、排在后面**；
- 整组会被重新编号（0..n-1），不做局部覆盖 —— 否则会和未改动过的分组并列，
  并列时只剩"按名称"这一条兜底规则，用户看到的顺序会莫名其妙；
- 不存在的分组名直接忽略，不报错（前端提交时可能刚删掉一个组）；
- 新分组不必先创建 —— 新增或更新数据源时带上 `groupName` 即自动出现，
  排序值排在现有分组之后；把数据源挪进已有分组时，该行对齐目标分组的排序值。

---

## 六、数据资产扫描接口

### 6.1 发起扫描

```
POST /discovery/sources/{sourceId}/scans
```

**请求体：**

```json
{
  "scanType": "FULL",
  "triggerType": "MANUAL"
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 5001,
    "sourceId": 101,
    "name": "生产库-用户中心 扫描",
    "scanType": "FULL",
    "triggerType": "MANUAL",
    "status": "PENDING",
    "createdBy": 1,
    "createdAt": "2026-09-08T10:30:00.123456"
  }
}
```

**语义：**

- **异步执行**：接口只负责落库并提交任务，立即返回 `PENDING`；后续用
  `GET /discovery/scans/{taskId}` 轮询。状态机：
  `PENDING → RUNNING → COMPLETED / FAILED / CANCELLED`。
- 同一数据源同一时刻**只允许一个未结束的任务**（`PENDING` / `RUNNING`），
  再次提交返回 `code=2` 并提示先等待或取消 —— 两个扫描同时写同一批资产行会互相覆盖。
- 执行成功：`totalSchemas` / `totalTables` / `totalColumns` 写入统计，
  数据源 `lastScanAt` / `lastScanStatus=SUCCESS` 同步更新。
- 执行失败：任务 `status=FAILED`，`errorMessage` 记一句原因（不写堆栈），
  数据源 `lastScanStatus=FAILED` + `scanErrorMsg`。
- 扫描只刷新**结构信息**（类型、长度、是否主键、注释…），
  **不覆盖**已有的人工分级（`sensitivity` / `category` / `manualOverride` 等）；
  系统 schema（`pg_*` / `information_schema`）不进入资产清单。
- 当前支持 **PostgreSQL / MySQL**。MySQL 的扫描范围是数据源里配置的那个库
  （MySQL 没有 schema，库即 catalog）：同实例的其他库请另建数据源 —— 这也符合
  最小权限，扫描账号通常只被授权到业务库。Oracle / SQL Server / MongoDB / Redis
  暂不支持，任务会被直接置为 `FAILED` 并写明原因。

### 6.2 查询扫描任务列表

```
GET /discovery/sources/{sourceId}/scans?page=1&size=20&status=COMPLETED
```

### 6.3 查询扫描任务详情

```
GET /discovery/scans/{taskId}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 5001,
    "name": "生产库-用户中心 扫描",
    "sourceId": 101,
    "scanType": "FULL",
    "triggerType": "MANUAL",
    "status": "COMPLETED",
    "totalSchemas": 3,
    "totalTables": 45,
    "totalColumns": 380,
    "sensitiveFound": null,
    "startedAt": "2026-09-08T10:30:05.001",
    "completedAt": "2026-09-08T10:31:22.940",
    "createdBy": 1
  }
}
```

> 响应是 `scan_task` 实体本身：没有 `sourceName`（要显示名字需按 `sourceId` 另查），
> `createdBy` 是用户 ID 而不是用户名，`sensitiveFound` 属于分类分级阶段（ID 21/22）的产出，
> 当前扫描不填。

### 6.4 配置定时扫描

```
PUT /discovery/sources/{sourceId}/schedule
```

**请求体：**

```json
{
  "enabled": true,
  "cronExpression": "0 0 2 * * *"
}
```

**语义：**

- 每个数据源**最多一个**定时扫描，配置存在 `data_source` 上
  （`schedule_cron` / `schedule_enabled` / `schedule_next_run_at`）。
- cron 支持 **6 段**（秒 分 时 日 月 周）与 **5 段**（自动补秒位，等价于 crontab 习惯）；
  统一存成 6 段。`?` 可用（Spring 6 里与 `*` 等价）。非法表达式返回 `code=2` 并附示例。
- `enabled=true` 时 `cronExpression` 必填；`enabled=false` 可不传 cron（保留原表达式、只关闭）。
- 调度由服务端每 `sable.discovery.schedule.poll-interval`（默认 30 秒）轮询一次：
  到期即提交一个 `triggerType=SCHEDULED` 的扫描任务（与手工扫描走同一条异步链路）。
  因此**触发精度是轮询间隔**，不是秒级精确。
- 停机期间错过的槽位**只补一次**（不会把积压的 N 个时间点全部回放）；
  若到期时该数据源已有扫描在跑，这一轮跳过并记日志，下一轮按新槽位继续。
- 配置与下次执行时间可从 `GET /discovery/sources/{id}` 读回：
  `scheduleCron` / `scheduleEnabled` / `scheduleNextRunAt`。

### 6.5 取消扫描任务

```
POST /discovery/scans/{taskId}/cancel
```

**语义：**

- 只对 `PENDING` / `RUNNING` 生效；重复取消（已是 `CANCELLED`）视为幂等成功。
- 任务已 `COMPLETED` / `FAILED` 时返回 `code=2`（"任务已结束，无法取消"）。
- 执行线程在每个 schema / 表之间检查状态，尽快停下；**取消后的完成/失败结果不会
  覆盖 `CANCELLED`**（状态写入都带 `WHERE status = 'RUNNING'` 条件）。
- 取消后数据源 `lastScanStatus` 记为 `PARTIAL` —— 资产可能只写入了一部分。

---

## 七、数据资产查询接口

### 7.1 查询数据资产树

```
GET /discovery/sources/{sourceId}/assets/tree
```

**响应：**

```json
{
  "code": 0,
  "data": [
    {
      "schemaName": "public",
      "tableCount": 45,
      "tables": [
        {
          "tableName": "t_user",
          "rowCount": 150000,
          "columnCount": 12,
          "sensitiveColumnCount": 5,
          "columns": [
            {
              "columnName": "phone",
              "dataType": "VARCHAR(20)",
              "sensitivity": "SPI",
              "category": "个人基本信息",
              "matchedRule": "手机号识别",
              "manualOverride": false
            }
          ]
        }
      ]
    }
  ]
}
```

### 7.2 查询字段列表（分页）

```
GET /discovery/sources/{sourceId}/columns
    ?page=1&size=20
    &sensitivity=SPI
    &category=个人基本信息
    &keyword=phone
    &tableName=t_user
```

### 7.3 修改字段分级（人工修正）

```
PUT /discovery/columns/{columnId}/sensitivity
```

**请求体：**

```json
{
  "sensitivity": "PI",
  "category": "个人基本信息",
  "reason": "该字段存储的是企业联系电话，非个人手机号"
}
```

### 7.4 批量修改分级

```
PUT /discovery/columns/batch-sensitivity
```

**请求体：**

```json
{
  "columnIds": [201, 202, 203],
  "sensitivity": "GENERAL",
  "category": "企业信息",
  "reason": "批量修正为非敏感字段"
}
```

### 7.5 导出资产清单

```
GET /discovery/sources/{sourceId}/export
    ?format=EXCEL
    &sensitivity=SPI
    &includeMetadata=true
```

**响应：** `Content-Type: application/octet-stream`，文件下载。

---

## 八、分类分级规则接口

### 8.1 查询规则列表

```
GET /classify/rules?page=1&size=20&category=IDENTITY&sensitivity=SPI&status=ACTIVE&keyword=身份证
```

### 8.2 查询规则详情

```
GET /classify/rules/{id}
```

### 8.3 创建规则

```
POST /classify/rules
```

**请求体：**

```json
{
  "name": "身份证号识别",
  "description": "匹配18位中国大陆居民身份证号",
  "category": "IDENTITY",
  "sensitivity": "SPI",
  "matchType": "REGEX",
  "pattern": "^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
  "priority": 100
}
```

### 8.4 更新规则

```
PUT /classify/rules/{id}
```

### 8.5 启用/停用规则

```
PUT /classify/rules/{id}/status
```

**请求体：**

```json
{
  "status": "DISABLED"
}
```

### 8.6 删除规则

```
DELETE /classify/rules/{id}
```

> 内置规则 (`is_builtin=true`) 不可删除，只能停用（`PUT /classify/rules/{id}/status`）。

### 8.7 规则测试

```
POST /classify/rules/test
```

对**当前租户全部启用中的规则**跑一次识别，返回命中的规则（按 `priority` 从高到低）。
不是"只测某一条规则"——传 `ruleId` 测单条没有对应的实现，接口按字段信息匹配整个规则库。

**请求体：**

```json
{
  "columnName": "user_phone",
  "columnComment": "用户手机号",
  "samples": ["13800138000"]
}
```

`columnName` / `columnComment` / `samples` 均可选，但至少传一个；三者都空时返回空数组。

**响应：**

```json
{
  "code": 0,
  "data": [
    {
      "ruleId": 12,
      "ruleName": "手机号识别",
      "category": "个人基本信息",
      "sensitivity": "PI"
    }
  ]
}
```

**匹配语义（与内置规则一致）：**

| matchType | pattern 形态 | 命中条件 |
|---|---|---|
| `KEYWORD` | JSON 数组 `["手机号","mobile"]` | 任一关键词出现在字段名 / 注释 / 采样值中（大小写不敏感） |
| `REGEX` | 正则字符串 | 正则对字段名 / 注释 / 采样值 `find()` 命中 |
| `COMPOSITE` | `{"regex":"...","keywords":[...],"min_match":n}` | 正则命中记 1 分、关键词命中记 1 分，达到 `min_match`（默认 2） |
| `SAMPLE` | 正则字符串 | **只**对采样值匹配；没有采样值时不命中 |

单条规则写坏（正则/JSON 非法）只跳过这一条并记 WARN，不影响其他规则。
内置规则共 **40 条**（建租户时随 `db/tenant/V5__builtin_classify_rules.sql` 写入），
覆盖个人基本信息、身份信息、生物特征、财务信息、健康医疗、位置信息、账号凭证、企业机密等分类。

### 8.8 查询分类分级标准

```
GET /classify/standards?type=PIPL
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "type": "PIPL",
    "version": "1.0",
    "categories": [
      {
        "code": "PERSONAL_BASIC",
        "name": "个人基本信息",
        "levels": ["PI", "SPI"],
        "description": "姓名、生日、手机号、地址等"
      },
      {
        "code": "IDENTITY",
        "name": "身份信息",
        "levels": ["SPI"],
        "description": "身份证号、护照号、军官证号等"
      },
      {
        "code": "BIOLOGICAL",
        "name": "生物特征",
        "levels": ["SPI"],
        "description": "指纹、人脸、虹膜、声纹等"
      },
      {
        "code": "FINANCIAL",
        "name": "财务信息",
        "levels": ["SPI"],
        "description": "银行卡号、存款、交易记录等"
      }
    ]
  }
}
```

---

## 九、审计日志接口

### 9.1 查询审计日志（分页）

```
GET /audit/logs
    ?page=1&size=20
    &userId=42
    &operation=QUERY
    &targetLevel=SPI
    &sourceIp=192.168.1
    &startTime=2026-09-01T00:00:00+08:00
    &endTime=2026-09-08T23:59:59+08:00
    &keyword=t_user
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 900001,
        "userId": 42,
        "userName": "张三",
        "operation": "QUERY",
        "targetDesc": "user_center.public.t_user.phone",
        "targetLevel": "SPI",
        "sourceIp": "192.168.1.50",
        "requestPath": "/api/v1/users/phone",
        "requestMethod": "GET",
        "result": "SUCCESS",
        "durationMs": 23,
        "createdAt": "2026-09-08T10:25:30+08:00"
      }
    ],
    "total": 1560,
    "page": 1,
    "size": 20,
    "pages": 78
  }
}
```

### 9.2 查询审计日志详情

```
GET /audit/logs/{id}
```

### 9.3 审计统计

```
GET /audit/statistics
    ?startTime=2026-09-01T00:00:00+08:00
    &endTime=2026-09-08T23:59:59+08:00
    &granularity=DAY
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "total": 125000,
    "byOperation": [
      { "operation": "QUERY", "count": 100000 },
      { "operation": "EXPORT", "count": 5000 },
      { "operation": "UPDATE", "count": 15000 },
      { "operation": "DELETE", "count": 5000 }
    ],
    "byLevel": [
      { "level": "SPI", "count": 30000 },
      { "level": "PI", "count": 60000 },
      { "level": "GENERAL", "count": 35000 }
    ],
    "trend": [
      { "date": "2026-09-01", "count": 15000 },
      { "date": "2026-09-02", "count": 16200 }
    ],
    "topUsers": [
      { "userId": 42, "userName": "张三", "count": 8500 },
      { "userId": 43, "userName": "李四", "count": 7200 }
    ],
    "topTargets": [
      { "targetDesc": "user_center.public.t_user.phone", "count": 12000 },
      { "targetDesc": "user_center.public.t_user.id_card", "count": 9500 }
    ]
  }
}
```

### 9.4 导出审计日志

```
GET /audit/logs/export
    ?format=CSV
    &startTime=...
    &endTime=...
    &operation=QUERY
    &targetLevel=SPI
```

### 9.5 日志完整性校验

```
POST /audit/logs/verify
```

**请求体：**

```json
{
  "startTime": "2026-09-01T00:00:00+08:00",
  "endTime": "2026-09-08T23:59:59+08:00"
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "totalChecked": 125000,
    "validCount": 125000,
    "invalidCount": 0,
    "invalidRecords": [],
    "verified": true
  }
}
```

### 9.6 SDK 审计事件上报

```
POST /audit/events
```

> 供非Java SDK或HTTP方式接入的客户端使用。

**请求体：**

```json
{
  "events": [
    {
      "userId": 42,
      "userName": "张三",
      "operation": "QUERY",
      "targetDesc": "user_center.public.t_user.phone",
      "targetLevel": "SPI",
      "sourceIp": "192.168.1.50",
      "requestPath": "/api/users/phone",
      "result": "SUCCESS",
      "durationMs": 23,
      "timestamp": "2026-09-08T10:25:30+08:00"
    }
  ]
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "accepted": 1,
    "rejected": 0
  }
}
```

---

## 十、告警管理接口

### 10.1 查询告警规则

```
GET /alert/rules?page=1&size=20&severity=CRITICAL&enabled=true
```

### 10.2 创建告警规则

```
POST /alert/rules
```

**请求体：**

```json
{
  "name": "非工作时间SPI数据访问",
  "description": "检测在非工作时间(18:00-09:00)访问敏感个人信息的行为",
  "ruleType": "TIME_BASED",
  "config": {
    "allowedHours": [9, 10, 11, 12, 13, 14, 15, 16, 17],
    "allowedDays": ["MON", "TUE", "WED", "THU", "FRI"],
    "targetLevel": "SPI"
  },
  "severity": "WARNING",
  "notifyChannels": ["SYSTEM", "EMAIL"],
  "notifyTargets": ["admin@example.com"]
}
```

### 10.3 更新告警规则

```
PUT /alert/rules/{id}
```

### 10.4 启用/停用告警规则

```
PUT /alert/rules/{id}/toggle
```

### 10.5 查询告警事件

```
GET /alert/events
    ?page=1&size=20
    &status=PENDING
    &severity=CRITICAL
    &startTime=...
    &endTime=...
```

### 10.6 处理告警事件

```
PUT /alert/events/{id}/handle
```

**请求体：**

```json
{
  "status": "RESOLVED",
  "remark": "确认为运维人员正常操作，已补录审批单"
}
```

### 10.7 批量处理告警

```
PUT /alert/events/batch-handle
```

**请求体：**

```json
{
  "eventIds": [301, 302, 303],
  "status": "ACKNOWLEDGED",
  "remark": "批量确认"
}
```

---

## 十一、合规评估接口

### 11.1 创建评估任务

```
POST /assessment/tasks
```

**请求体：**

```json
{
  "name": "2026年Q3 PIPL合规评估",
  "standardType": "PIPL",
  "standardVersion": "1.0",
  "description": "第三季度个人信息保护法合规评估"
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 6001,
    "name": "2026年Q3 PIPL合规评估",
    "standardType": "PIPL",
    "status": "DRAFT",
    "totalItems": 52,
    "createdAt": "2026-09-08T10:30:00+08:00"
  }
}
```

### 11.2 查询评估任务列表

```
GET /assessment/tasks?page=1&size=20&status=IN_PROGRESS&standardType=PIPL
```

### 11.3 查询评估任务详情

```
GET /assessment/tasks/{id}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "id": 6001,
    "name": "2026年Q3 PIPL合规评估",
    "standardType": "PIPL",
    "standardVersion": "1.0",
    "status": "IN_PROGRESS",
    "totalItems": 52,
    "passedItems": 35,
    "partialItems": 5,
    "failedItems": 8,
    "naItems": 4,
    "score": 78.50,
    "grade": "C",
    "assessorId": 42,
    "startedAt": "2026-09-08T10:30:00+08:00",
    "completedAt": null
  }
}
```

### 11.4 查询评估检查项

```
GET /assessment/tasks/{taskId}/items
    ?status=FAILED
    &category=技术措施
    &checkType=AUTO
    &page=1&size=20
```

### 11.5 填写检查项评估结果

```
PUT /assessment/tasks/{taskId}/items/{itemId}
```

**请求体：**

```json
{
  "status": "PARTIAL",
  "evidence": "已建立分类管理制度，但部分历史数据尚未完成分级",
  "evidenceFiles": ["file_001", "file_002"],
  "remark": "预计下月完成剩余数据分级"
}
```

### 11.6 执行自动评估

```
POST /assessment/tasks/{taskId}/auto-evaluate
```

> 触发系统对所有 AUTO 类型检查项进行自动评估。

**响应：**

```json
{
  "code": 0,
  "data": {
    "totalAutoItems": 20,
    "evaluated": 20,
    "passed": 15,
    "failed": 3,
    "partial": 2,
    "durationMs": 3500
  }
}
```

### 11.7 分配整改任务

```
PUT /assessment/tasks/{taskId}/items/{itemId}/rectify
```

**请求体：**

```json
{
  "assigneeId": 43,
  "dueDate": "2026-10-15",
  "gapDescription": "缺少个人信息保护影响评估制度",
  "suggestion": "参照PIPL第55条，建立PIA评估流程，覆盖以下场景：..."
}
```

### 11.8 完成评估

```
POST /assessment/tasks/{taskId}/complete
```

> 计算最终得分和等级，锁定评估结果。

### 11.9 查询评估标准检查项模板

```
GET /assessment/standards/{standardType}/items
    ?version=1.0
    &category=组织管理
```

---

## 十二、报告接口

### 12.1 生成合规评估报告

```
POST /reports/assessment
```

**请求体：**

```json
{
  "assessmentId": 6001,
  "templateId": "DEFAULT",
  "includeGapAnalysis": true,
  "includeSuggestion": true
}
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "reportId": 7001,
    "status": "GENERATING",
    "estimatedSeconds": 30
  }
}
```

### 12.2 生成数据资产报告

```
POST /reports/asset
```

**请求体：**

```json
{
  "sourceIds": [101, 102],
  "includeSensitivityDistribution": true,
  "includeCategoryDistribution": true
}
```

### 12.3 生成审计报告

```
POST /reports/audit
```

**请求体：**

```json
{
  "startTime": "2026-09-01T00:00:00+08:00",
  "endTime": "2026-09-30T23:59:59+08:00",
  "includeAnomalySummary": true,
  "includeStatistics": true
}
```

### 12.4 查询报告列表

```
GET /reports?page=1&size=20&type=ASSESSMENT
```

### 12.5 下载报告

```
GET /reports/{reportId}/download
```

**响应：** `Content-Type: application/pdf`，文件下载。

### 12.6 配置定期报告

```
POST /reports/schedules
```

**请求体：**

```json
{
  "reportType": "AUDIT",
  "cronExpression": "0 0 9 ? * MON",
  "notifyEmails": ["admin@example.com", "compliance@example.com"],
  "config": {
    "startTime": "2026-09-01T00:00:00+08:00",
    "endTime": "2026-09-30T23:59:59+08:00",
    "includeAnomalySummary": true
  }
}
```

---

## 十三、仪表盘接口

### 13.1 合规总览

```
GET /dashboard/overview
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "complianceScore": 78.5,
    "grade": "C",
    "lastAssessmentDate": "2026-09-08",
    "pendingRectifications": 8,
    "overdueRectifications": 2,
    "todayAlerts": 3,
    "unhandledAlerts": 5,
    "totalDataSources": 8,
    "totalSensitiveFields": 67,
    "auditLogToday": 15600,
    "anomalyThisWeek": 12
  }
}
```

### 13.2 数据资产概览

```
GET /dashboard/assets
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "totalSources": 8,
    "totalSchemas": 15,
    "totalTables": 230,
    "totalColumns": 2100,
    "sensitivityDistribution": [
      { "level": "SPI", "count": 67, "percentage": 3.2 },
      { "level": "PI", "count": 340, "percentage": 16.2 },
      { "level": "IMPORTANT", "count": 45, "percentage": 2.1 },
      { "level": "GENERAL", "count": 1648, "percentage": 78.5 }
    ],
    "categoryDistribution": [
      { "category": "个人基本信息", "count": 120 },
      { "category": "身份信息", "count": 45 },
      { "category": "财务信息", "count": 89 },
      { "category": "生物特征", "count": 12 }
    ],
    "unclassifiedColumns": 280
  }
}
```

### 13.3 审计趋势

```
GET /dashboard/audit-trend?days=30
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "dailyTrend": [
      { "date": "2026-08-10", "total": 14500, "anomaly": 2 },
      { "date": "2026-08-11", "total": 15200, "anomaly": 0 }
    ],
    "operationDistribution": [
      { "operation": "QUERY", "count": 350000 },
      { "operation": "EXPORT", "count": 12000 }
    ],
    "levelDistribution": [
      { "level": "SPI", "count": 85000 },
      { "level": "PI", "count": 180000 }
    ]
  }
}
```

---

## 十四、系统配置接口

### 14.1 查询系统配置

```
GET /system/configs?group=audit
```

### 14.2 更新系统配置

```
PUT /system/configs
```

**请求体：**

```json
{
  "configs": [
    {
      "group": "audit",
      "key": "log.retention.days",
      "value": "180"
    },
    {
      "group": "audit",
      "key": "log.batch.size",
      "value": "500"
    }
  ]
}
```

### 14.3 查询系统健康

```
GET /system/health
```

**响应：**

```json
{
  "code": 0,
  "data": {
    "status": "HEALTHY",
    "components": [
      { "name": "PostgreSQL", "status": "UP", "detail": "主库正常, 从库延迟2ms" },
      { "name": "Redis", "status": "UP", "detail": "3节点集群正常" },
      { "name": "RabbitMQ", "status": "UP", "detail": "消息积压: 0" },
      { "name": "Elasticsearch", "status": "UP", "detail": "3节点集群, 磁盘使用45%" },
      { "name": "MinIO", "status": "UP", "detail": "存储使用: 23.5GB" }
    ],
    "jvm": {
      "heapUsedMb": 512,
      "heapMaxMb": 2048,
      "threadCount": 85,
      "uptime": "3d 12h 45m"
    }
  }
}
```

---

## 十五、文件接口

### 15.1 上传文件

```
POST /files/upload
Content-Type: multipart/form-data
```

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 上传的文件 |
| bizType | String | 是 | 业务类型：ASSESSMENT_EVIDENCE / REPORT / EXPORT |
| bizId | String | 否 | 关联业务ID |

**响应：**

```json
{
  "code": 0,
  "data": {
    "fileId": "file_001",
    "fileName": "合规制度v2.pdf",
    "fileSize": 1048576,
    "contentType": "application/pdf",
    "url": "/api/v1/files/file_001/download"
  }
}
```

### 15.2 下载文件

```
GET /files/{fileId}/download
```

---

## 附录A：权限矩阵

| 接口 | SUPER_ADMIN | COMPLIANCE_OFFICER | AUDITOR | DATA_STEWARD | VIEWER |
|------|:-----------:|:------------------:|:-------:|:------------:|:------:|
| 租户管理 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 用户管理 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 数据源管理 | ✅ | ✅ | ❌ | ✅ | ❌ |
| 扫描执行 | ✅ | ✅ | ❌ | ✅ | ❌ |
| 资产查看 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 资产修改 | ✅ | ✅ | ❌ | ✅ | ❌ |
| 规则管理 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 审计日志查看 | ✅ | ✅ | ✅ | ✅* | ✅* |
| 审计日志导出 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 告警规则管理 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 告警事件处理 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 合规评估 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 报告生成/导出 | ✅ | ✅ | ✅ | ❌ | ❌ |
| 仪表盘 | ✅ | ✅ | ✅ | ✅ | ✅* |
| 系统配置 | ✅ | ❌ | ❌ | ❌ | ❌ |

> ✅* = 仅本部门/本业务线范围

## 附录B：接口清单汇总

| 模块 | 接口数 | 路径前缀 |
|------|--------|---------|
| 认证 | 4 | `/auth` |
| 租户管理 | 6 | `/system/tenants` |
| 用户权限 | 12 | `/system/users`, `/system/roles` |
| 数据源管理 | 7 | `/discovery/sources` |
| 扫描任务 | 5 | `/discovery/scans`, `/discovery/sources/{id}/scans` |
| 数据资产 | 5 | `/discovery/sources/{id}/assets`, `/discovery/columns` |
| 分类分级规则 | 8 | `/classify/rules`, `/classify/standards` |
| 审计日志 | 6 | `/audit/logs`, `/audit/events`, `/audit/statistics` |
| 告警管理 | 7 | `/alert/rules`, `/alert/events` |
| 合规评估 | 9 | `/assessment/tasks`, `/assessment/standards` |
| 报告 | 6 | `/reports` |
| 仪表盘 | 3 | `/dashboard` |
| 系统配置 | 3 | `/system/configs`, `/system/health` |
| 文件 | 2 | `/files` |
| **合计** | **83** | |
