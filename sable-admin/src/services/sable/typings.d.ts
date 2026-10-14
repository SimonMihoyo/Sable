/**
 * 后端接口的类型定义 —— **以实际返回为准**。
 *
 * ⚠️ 这里刻意不照抄 `doc/数据合规系统-API接口设计文档.md`：实测发现该文档与代码有多处漂移
 * （登录响应多写了 `tokenType` 与 `user` 两个并不存在的字段；`/system/users/me` 的响应样例
 * 也承诺了当时尚未装配的 `roles`/`permissions`）。前端一旦按文档写死，接上真服务就是一连串
 * `undefined`。凡本文件与文档不一致处，**以本文件为准**，并已在注释里写明实际形态。
 */
declare namespace SableAPI {
  /**
   * 统一响应包装，对应后端的 `R<T>`。
   *
   * - `code` **成功是 0**（不是 200）—— 这是业务码，与 HTTP 状态码各管一层；
   * - 业务失败时 HTTP 状态码**仍是 200**，只有 `code` 非 0。所以判断成败一律看 `code`；
   * - `timestamp` 是 epoch 毫秒；`traceId` 由后端日志追踪用，排查问题时把它抄给后端很有用。
   */
  type R<T> = {
    code: number;
    message: string;
    data?: T;
    timestamp?: number;
    traceId?: string;
  };

  /** 登录参数。`mfaCode` 当前后端尚未校验（启用 MFA 的账号会被直接拒绝，见 ErrorCode）。 */
  type LoginParams = {
    username: string;
    password: string;
    mfaCode?: string;
  };

  /**
   * 登录 / 刷新令牌的响应体。
   *
   * ⚠️ **没有 `user` 字段，也没有 `tokenType`**（与 API 文档 2.1 节不符）。
   * 登录成功后要拿用户信息，必须再请求一次 `GET /system/users/me`。
   */
  type LoginResult = {
    accessToken: string;
    refreshToken: string;
    /** 访问令牌有效期（秒），当前后端配置是 1800。 */
    expiresIn: number;
    /**
     * 是否需要二次 MFA 验证。
     *
     * 目前**恒为 false**：后端对已开启 MFA 的账号直接返回错误码 40004，走不到这个分支。
     * 保留字段是为了将来接入 TOTP 时无需再改响应结构。
     */
    mfaRequired: boolean;
  };

  /**
   * 当前登录用户，对应后端 `UserResponse`（脱敏投影）。
   *
   * `roles` / `permissions` 已由后端装配（本轮补齐）：
   * - `roles` 取当前租户下授予的角色 `name`，如 `["SUPER_ADMIN", "COMPLIANCE_OFFICER"]`；
   * - `permissions` 是这些角色 `permissions` 列的并集去重。**当前没有权限码字典**，
   *   值即角色上配置的自由字符串。
   *
   * 用户不存在或已软删除时，接口返回错误码 40001 而不是 null。
   */
  type CurrentUser = {
    id: number;
    username: string;
    displayName?: string;
    email?: string;
    phone?: string;
    avatarUrl?: string;
    /** ACTIVE / LOCKED / DISABLED */
    status?: string;
    mfaEnabled?: boolean;
    lastLoginAt?: string;
    lockedUntil?: string;
    createdAt?: string;
    updatedAt?: string;
    roles: string[];
    permissions: string[];
  };

  /**
   * 分页结果，对应后端 `PageResult<T>`。
   *
   * ⚠️ **没有 `pages` 字段**（API 文档 1.2 节的示例里多写了这个）；总页数请自行按
   * `Math.ceil(total / size)` 计算。
   */
  type PageResult<T> = {
    page: number;
    size: number;
    total: number;
    records: T[];
  };

  /** 分页查询入参，对应后端 `PageQuery`。注意后端**没有实现任何排序参数**。 */
  type PageQuery = {
    page?: number;
    size?: number;
    keyword?: string;
  };

  /**
   * 租户，对应后端 `Tenant` 实体（`public.tenant`）。
   *
   * ⚠️ 两个取值域**不是**直觉上的那套，前端不要按 `ACTIVE/DISABLED` 写：
   * - `status`：`ACTIVE` / `SUSPENDED` / `EXPIRED`；
   * - `planType`：`BASIC` / `PROFESSIONAL` / `ENTERPRISE`。
   * 有效期由 `expiredAt` 承载，不靠把 status 改成 `EXPIRED` 来表达。
   */
  type Tenant = {
    id: number;
    /** 租户名称。 */
    name: string;
    /** 租户编码。唯一，用于生成 schema 名，**创建后不可修改**。 */
    code: string;
    /** BASIC / PROFESSIONAL / ENTERPRISE */
    planType?: string;
    /** ACTIVE / SUSPENDED / EXPIRED */
    status?: string;
    contactName?: string;
    contactEmail?: string;
    contactPhone?: string;
    /** 租户级配置，JSON 字符串（后端以裸 String 承载 JSONB 列） */
    config?: string;
    /** 到期时间。 */
    expiredAt?: string;
    /** 软删除标记。列表接口已经过滤掉，正常情况下拿不到有值的数据。 */
    deletedAt?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  /** 角色，对应后端 `Role` 实体。 */
  type Role = {
    id: number;
    tenantId?: number;
    name: string;
    displayName?: string;
    description?: string;
    isSystem?: boolean;
    /** JSON 字符串，形如 `["data_source:read"]` */
    permissions?: string;
    createdAt?: string;
    updatedAt?: string;
  };

  /** 系统用户列表项，与 CurrentUser 同源但列表场景不保证角色已装配。 */
  type SysUser = CurrentUser;

  /** 修改密码入参。 */
  type PasswordChangeParams = {
    oldPassword: string;
    newPassword: string;
  };
}
