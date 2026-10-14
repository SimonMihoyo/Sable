/**
 * 权限定义 —— 决定"菜单里出现什么、路由让不让进"。
 *
 * @see https://umijs.org/docs/max/access#access
 *
 * ⚠️ **这是显示控制，不是安全边界。**
 * 后端当前的接口级鉴权尚未落地（没有 `@PreAuthorize`，权限码字典也还没定），
 * 也就是说：任何登录用户绕过界面直接调接口，都能拿到数据。
 * 这里做的事是"不把用户用不上的功能摆在他面前"，真正的拦截要等后端补上校验。
 * 千万不要因为菜单藏起来了，就认为权限已经做好了。
 */

/**
 * 权限码 —— 逐字取自 `V1__init_schema.sql` 里 `role.permissions` 的样例注释，
 * 以及 `db/dev/R__dev_seed.sql` 给三个预置角色配置的实际取值。不额外发明。
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

/** 系统预置角色标识（角色的 `name` 列）。 */
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
