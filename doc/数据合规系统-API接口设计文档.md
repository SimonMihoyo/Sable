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

**成功响应：**

```json
{
  "code": 200,
  "message": "success",
  "data": { },
  "timestamp": "2026-09-08T10:30:00.123+08:00",
  "traceId": "abc123def456"
}
```

**分页响应：**

```json
{
  "code": 200,
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

| 范围 | 模块 | 说明 |
|------|------|------|
| 200 | 全局 | 成功 |
| 40001-40099 | 认证鉴权 | 登录、Token、权限相关 |
| 40101-40199 | 租户管理 | 租户相关错误 |
| 40201-40299 | 数据源管理 | 数据源连接、扫描相关 |
| 40301-40399 | 分类分级 | 规则、分级相关 |
| 40401-40499 | 审计引擎 | 日志查询、SDK相关 |
| 40501-40599 | 合规评估 | 评估、检查项相关 |
| 40601-40699 | 告警引擎 | 规则、事件相关 |
| 40701-40799 | 报告引擎 | 报告生成相关 |
| 50001-50099 | 系统内部 | 未预期错误 |

**常用错误码：**

| 错误码 | 含义 |
|--------|------|
| 200 | 成功 |
| 40001 | 用户名或密码错误 |
| 40002 | Token已过期 |
| 40003 | Token无效 |
| 40004 | 无权限访问 |
| 40005 | MFA验证失败 |
| 40006 | 账号已锁定 |
| 40101 | 租户不存在 |
| 40102 | 租户已停用 |
| 40103 | 超出租户配额限制 |
| 40201 | 数据源不存在 |
| 40202 | 数据源连接失败 |
| 40203 | 数据源正在扫描中 |
| 40204 | 扫描任务不存在 |
| 40301 | 分类规则不存在 |
| 40302 | 内置规则不可删除 |
| 40401 | 审计日志查询参数无效 |
| 40501 | 评估任务不存在 |
| 40502 | 评估任务状态不允许此操作 |
| 50001 | 系统内部错误 |

### 1.4 认证机制

**请求头：**

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
X-Tenant-Id: 1001          ← 超级管理员跨租户操作时需要
X-Request-Id: req_abc123   ← 客户端生成的请求追踪ID（可选）
```

**Token 结构 (JWT Payload)：**

```json
{
  "sub": "42",
  "tenantId": 1001,
  "username": "admin",
  "roles": ["SUPER_ADMIN"],
  "permissions": ["data_source:read", "data_source:write", "..."],
  "iat": 1725768600,
  "exp": 1725770400
}
```

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
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 1800,
    "tokenType": "Bearer",
    "user": {
      "id": "42",
      "username": "admin",
      "displayName": "系统管理员",
      "email": "admin@example.com",
      "roles": ["SUPER_ADMIN"],
      "mfaEnabled": true
    }
  }
}
```

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

**响应：** 同登录响应结构。

### 2.3 退出登录

```
POST /auth/logout
```

**响应：**

```json
{
  "code": 200,
  "message": "success"
}
```

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
  "code": 200,
  "data": {
    "id": "1001",
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
  "code": 200,
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
  "department": "技术部"
}
```

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
  "roleIds": ["1", "2"]
}
```

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
  "code": 200,
  "data": {
    "id": "42",
    "username": "admin",
    "displayName": "系统管理员",
    "email": "admin@example.com",
    "phone": "13800138000",
    "department": "技术部",
    "roles": [
      {
        "id": "1",
        "name": "SUPER_ADMIN",
        "displayName": "超级管理员"
      }
    ],
    "permissions": ["*"],
    "mfaEnabled": true,
    "lastLoginAt": "2026-09-08T09:00:00+08:00"
  }
}
```

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
  "code": 200,
  "data": {
    "id": "101",
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

**响应：**

```json
{
  "code": 200,
  "data": {
    "success": true,
    "message": "连接成功",
    "serverVersion": "MySQL 8.0.35",
    "responseTimeMs": 45
  }
}
```

### 5.7 数据源分组管理

```
GET  /discovery/sources/groups          ← 查询分组列表
PUT  /discovery/sources/groups/reorder  ← 调整分组排序
```

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
  "code": 200,
  "data": {
    "taskId": "5001",
    "status": "PENDING",
    "createdAt": "2026-09-08T10:30:00+08:00"
  }
}
```

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
  "code": 200,
  "data": {
    "id": "5001",
    "sourceId": "101",
    "sourceName": "生产库-用户中心",
    "scanType": "FULL",
    "triggerType": "MANUAL",
    "status": "COMPLETED",
    "totalSchemas": 3,
    "totalTables": 45,
    "totalColumns": 380,
    "sensitiveFound": 67,
    "startedAt": "2026-09-08T10:30:05+08:00",
    "completedAt": "2026-09-08T10:31:22+08:00",
    "createdBy": "admin"
  }
}
```

### 6.4 配置定时扫描

```
PUT /discovery/sources/{sourceId}/schedule
```

**请求体：**

```json
{
  "enabled": true,
  "cronExpression": "0 0 2 * * ?",
  "scanType": "INCREMENTAL"
}
```

### 6.5 取消扫描任务

```
POST /discovery/scans/{taskId}/cancel
```

---

## 七、数据资产查询接口

### 7.1 查询数据资产树

```
GET /discovery/sources/{sourceId}/assets/tree
```

**响应：**

```json
{
  "code": 200,
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
  "columnIds": ["201", "202", "203"],
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

> 内置规则 (`is_builtin=true`) 不可删除。

### 8.7 规则测试

```
POST /classify/rules/test
```

**请求体：**

```json
{
  "ruleId": "1",
  "testValues": ["110101199001011234", "abc123", "13800138000"]
}
```

**响应：**

```json
{
  "code": 200,
  "data": [
    { "value": "110101199001011234", "matched": true },
    { "value": "abc123", "matched": false },
    { "value": "13800138000", "matched": false }
  ]
}
```

### 8.8 查询分类分级标准

```
GET /classify/standards?type=PIPL
```

**响应：**

```json
{
  "code": 200,
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
  "code": 200,
  "data": {
    "records": [
      {
        "id": "900001",
        "userId": "42",
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
  "code": 200,
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
      { "userId": "42", "userName": "张三", "count": 8500 },
      { "userId": "43", "userName": "李四", "count": 7200 }
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
  "code": 200,
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
      "userId": "42",
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
  "code": 200,
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
  "eventIds": ["301", "302", "303"],
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
  "code": 200,
  "data": {
    "id": "6001",
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
  "code": 200,
  "data": {
    "id": "6001",
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
    "assessorId": "42",
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
  "code": 200,
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
  "assigneeId": "43",
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
  "assessmentId": "6001",
  "templateId": "DEFAULT",
  "includeGapAnalysis": true,
  "includeSuggestion": true
}
```

**响应：**

```json
{
  "code": 200,
  "data": {
    "reportId": "7001",
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
  "sourceIds": ["101", "102"],
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
  "code": 200,
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
  "code": 200,
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
  "code": 200,
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
  "code": 200,
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
  "code": 200,
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
