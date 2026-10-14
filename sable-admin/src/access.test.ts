import { describe, expect, it } from 'vitest';
import access, { Permission, RoleName } from './access';

/** 造一个最小可用的当前用户；只关心 roles / permissions 两项。 */
function stateWith(roles: string[], permissions: string[]) {
  return {
    currentUser: {
      id: 1,
      username: 'alice',
      roles,
      permissions,
    } as SableAPI.CurrentUser,
  };
}

describe('access', () => {
  describe('isLogin', () => {
    it('有 currentUser 时为 true', () => {
      expect(access(stateWith([], [])).isLogin).toBe(true);
    });

    it('currentUser 为 undefined 时为 false', () => {
      expect(access({ currentUser: undefined }).isLogin).toBe(false);
    });

    it('initialState 整体为 undefined 时为 false', () => {
      expect(access(undefined).isLogin).toBeFalsy();
    });
  });

  describe('canManageSystem', () => {
    it('持有 system:manage 时为 true', () => {
      const result = access(
        stateWith([RoleName.SUPER_ADMIN], [Permission.SYSTEM_MANAGE]),
      );
      expect(result.canManageSystem).toBe(true);
    });

    it('只有其它权限时为 false', () => {
      const result = access(
        stateWith([RoleName.AUDITOR], [Permission.AUDIT_LOG_READ]),
      );
      expect(result.canManageSystem).toBe(false);
    });

    /**
     * 这里按**权限码**判断而不是按角色名。租户自建的角色只要带上了 system:manage
     * 就应当放行 —— 这条断言用来钉住该决定：若哪天被改成
     * `roles.includes('SUPER_ADMIN')`，它会立刻变红。
     */
    it('自定义角色只要带 system:manage 也放行', () => {
      const result = access(
        stateWith(['MY_CUSTOM_ROLE'], [Permission.SYSTEM_MANAGE]),
      );
      expect(result.canManageSystem).toBe(true);
    });
  });

  describe('各模块读写权限', () => {
    it('按权限码逐项映射', () => {
      const result = access(
        stateWith(
          [RoleName.COMPLIANCE_OFFICER],
          [
            Permission.DATA_SOURCE_READ,
            Permission.DATA_SOURCE_WRITE,
            Permission.ASSESSMENT_READ,
          ],
        ),
      );

      expect(result.canReadDataSource).toBe(true);
      expect(result.canWriteDataSource).toBe(true);
      expect(result.canReadAssessment).toBe(true);
      // 未授予的必须是 false —— 不能"有读就等于有写"
      expect(result.canWriteAssessment).toBe(false);
      expect(result.canReadAuditLog).toBe(false);
      expect(result.canExportReport).toBe(false);
    });

    it('没有任何权限时全部为 false', () => {
      const result = access(stateWith([], []));
      expect(result.canManageSystem).toBe(false);
      expect(result.canReadDataSource).toBe(false);
      expect(result.canReadReport).toBe(false);
      expect(result.hasAnyRole).toBe(false);
    });
  });

  describe('hasAnyRole', () => {
    it('有角色时为 true', () => {
      expect(access(stateWith([RoleName.VIEWER], [])).hasAnyRole).toBe(true);
    });

    it('无角色时为 false（这种账号什么也做不了）', () => {
      expect(
        access(stateWith([], [Permission.REPORT_READ])).hasAnyRole,
      ).toBe(false);
    });
  });

  /**
   * `roles` / `permissions` 允许缺省 —— 后端列表类接口目前不装配这两项
   * （只有 `/system/users/me` 装了）。缺失时必须按空数组处理而不是抛异常，
   * 否则一进首页就白屏。
   */
  it('缺少 roles / permissions 字段时不抛异常且按空处理', () => {
    const result = access({
      currentUser: { id: 1, username: 'bob' } as SableAPI.CurrentUser,
    });
    expect(result.hasAnyRole).toBe(false);
    expect(result.canManageSystem).toBe(false);
  });
});
