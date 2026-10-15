/**
 * 权限定义 —— 决定"菜单里出现什么、路由让不让进"。
 *
 * @see https://umijs.org/docs/max/access#access
 *
 * ⚠️ **安全边界在服务端，不在这里。**
 *
 * 服务端已经用 `@PreAuthorize` 做了接口级校验：藏起来的接口绕过界面直接调，
 * 会拿到 `403 + 40801`。于是下面这两句话都要记住，它们各自对应一个真实的误判：
 *
 * - **别以为"菜单藏起来了"就等于"这个功能已经受保护"** —— 那只说明界面没暴露它；
 * - **也别以为"菜单显示出来了"就等于"点了会成功"** —— 决定权仍在服务端。
 *
 * 后一种情况会真实发生：令牌里的权限最长 30 分钟（access token TTL）才更新，
 * 而这里的数据来自 `/system/users/me`，它是按**数据库当前状态**装配的。
 * 刚给某个角色加了权限时，菜单会立刻出现，但接口可能还要等到令牌续期才放行 ——
 * 这是刻意的取舍（见 `AuthServiceImpl#issueTokens`），不是 bug。
 */

/**
 * 权限码。
 *
 * <p>**服务端的权威定义是 `me.kirara.sable.common.Permission`**，这里是一份必须同步的镜像
 * （服务端有一条测试比对它与种子数据是否一致，但管不到这边）。改那边时记得改这里，
 * 否则会出现"菜单显示了、接口却 403"。</p>
 *
 * <p>另外这些码是权限码而不是角色名：服务端鉴权一律按权限码判断，
 * 租户自建角色只要被授予了同样的码就一样能用。所以这里也**不要**改成按角色名判断。</p>
 */
export const Permission = {
  DATA_SOURCE_READ: 'data_source:read',
  DATA_SOURCE_WRITE: 'data_source:write',
  AUDIT_LOG_READ: 'audit_log:read',
  AUDIT_LOG_EXPORT: 'audit_log:export',
  ASSESSMENT_READ: 'assessment:read',
  ASSESSMENT_WRITE: 'assessment:write',
  REPORT_READ: 'report:read',
  REPORT_EXPORT: 'report:export',
  SYSTEM_MANAGE: 'system:manage',
} as const;

/**
 * 系统预置角色标识（角色的 `name` 列）。
 *
 * <p>五个角色取自 `FR-6.2.1`，与 `db/dev/R__dev_seed.sql` 里的预置角色一致。
 * **这里只用于展示**（例如在用户列表里显示角色名）—— 权限判断请一律走上面的权限码，
 * 别按角色名分支：租户可以自建角色（`FR-6.2.2`），写死角色名会让自建角色天然失效。</p>
 */
export const RoleName = {
  SUPER_ADMIN: 'SUPER_ADMIN',
  COMPLIANCE_OFFICER: 'COMPLIANCE_OFFICER',
  AUDITOR: 'AUDITOR',
  DATA_STEWARD: 'DATA_STEWARD',
  VIEWER: 'VIEWER',
} as const;

export default function access(
  initialState: { currentUser?: SableAPI.CurrentUser } | undefined,
) {
  const user = initialState?.currentUser;
  const roles: string[] = user?.roles ?? [];
  const permissions: string[] = user?.permissions ?? [];

  const has = (code: string) => permissions.includes(code);

  return {
    /**
     * 是否已登录。路由守卫用它判断要不要拦。
     *
     * <p>用"有没有 currentUser"而不是"内存里有没有 accessToken"：后者在刷新页面后
     * 必然为空（access 只存内存），会导致刚登录的用户一刷新就被赶回登录页。</p>
     */
    isLogin: !!user,

    /**
     * 系统管理（租户 / 用户 / 角色）。
     *
     * <p>按权限码判断而不是硬编码角色名 —— 租户可以自建角色，只要给了
     * `system:manage` 就应当放行。当前只有 SUPER_ADMIN 持有它。</p>
     */
    canManageSystem: has(Permission.SYSTEM_MANAGE),

    // ── 以下为后续 Sprint 的模块预留：菜单项尚未加入 config/routes.ts，
    //    先把判定写好，等对应页面落地时直接引用 ──
    canReadDataSource: has(Permission.DATA_SOURCE_READ),
    canWriteDataSource: has(Permission.DATA_SOURCE_WRITE),
    canReadAuditLog: has(Permission.AUDIT_LOG_READ),
    canExportAuditLog: has(Permission.AUDIT_LOG_EXPORT),
    canReadAssessment: has(Permission.ASSESSMENT_READ),
    canWriteAssessment: has(Permission.ASSESSMENT_WRITE),
    canReadReport: has(Permission.REPORT_READ),
    canExportReport: has(Permission.REPORT_EXPORT),

    /** 是否持有任一角色 —— 没有角色的账号实际上什么也做不了，工作台据此给提示。 */
    hasAnyRole: roles.length > 0,
  };
}
