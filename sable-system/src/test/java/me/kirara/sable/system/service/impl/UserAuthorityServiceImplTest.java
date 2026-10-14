package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import me.kirara.sable.system.auth.UserAuthority;
import me.kirara.sable.system.entity.Role;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.entity.TenantUserRole;
import me.kirara.sable.system.mapper.RoleMapper;
import me.kirara.sable.system.mapper.TenantUserMapper;
import me.kirara.sable.system.mapper.TenantUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserAuthorityServiceImpl} 单元测试。
 *
 * <p>Mapper 用 Mockito 模拟，被验证的是"归属关系的选取与降级策略"。
 * 角色经 {@code tenant_user_role} 关联，因此一个用户可以装配到<b>多个</b>角色名。</p>
 */
@SuppressWarnings("unchecked")
class UserAuthorityServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 1001L;

    private TenantUserMapper tenantUserMapper;
    private TenantUserRoleMapper tenantUserRoleMapper;
    private RoleMapper roleMapper;
    private UserAuthorityServiceImpl service;

    @BeforeEach
    void setUp() {
        // lambdaQuery 靠 TableInfo 缓存把方法引用映射成列名，该缓存只在 Spring 启动时建立
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TenantUser.class);
        TableInfoHelper.initTableInfo(assistant, TenantUserRole.class);
        TableInfoHelper.initTableInfo(assistant, Role.class);

        tenantUserMapper = mock(TenantUserMapper.class);
        tenantUserRoleMapper = mock(TenantUserRoleMapper.class);
        roleMapper = mock(RoleMapper.class);
        service = new UserAuthorityServiceImpl(tenantUserMapper, tenantUserRoleMapper, roleMapper);
    }

    @Test
    @DisplayName("单个租户归属时装配租户与角色名")
    void singleMembership() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(5L, "COMPLIANCE_OFFICER")));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(TENANT_ID, authority.getTenantId().longValue());
        assertEquals(List.of("COMPLIANCE_OFFICER"), authority.getRoleNames());
    }

    @Test
    @DisplayName("同一租户下授予多个角色时全部装配，不取其一")
    void multipleRolesInSameTenant() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L), grant(6L), grant(7L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role(5L, "AUDITOR"), role(6L, "VIEWER"), role(7L, "DATA_STEWARD")));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(List.of("AUDITOR", "VIEWER", "DATA_STEWARD"), authority.getRoleNames(),
                "多角色必须全部进令牌，而不是只挑一个");
    }

    @Test
    @DisplayName("无租户归属（系统级用户）时租户为空、角色为空列表")
    void noMembership() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of());

        UserAuthority authority = service.resolve(USER_ID);

        assertNull(authority.getTenantId());
        assertTrue(authority.getRoleNames().isEmpty());
        verify(tenantUserRoleMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("属于多个租户时取最早加入的一个，并把排序下推到 SQL")
    void multipleMemberships() {
        when(tenantUserMapper.selectList(any()))
                .thenReturn(List.of(membership(7L, TENANT_ID), membership(8L, 1002L)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(6L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(6L, "VIEWER")));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(TENANT_ID, authority.getTenantId().longValue(),
                "应取查询结果第一条（最早加入），而不是最后一条");
        assertEquals(List.of("VIEWER"), authority.getRoleNames());

        // 「最早」必须由 SQL 的 ORDER BY 保证：若靠返回顺序，换数据库 / 换索引就会变
        ArgumentCaptor<LambdaQueryWrapper<TenantUser>> captor =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(tenantUserMapper, times(1)).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("ORDER BY"),
                "归属查询必须带排序：" + captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("角色不存在或已软删除时仍保留租户，只是角色为空")
    void roleGone() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L)));
        when(roleMapper.selectList(any())).thenReturn(List.of());

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(TENANT_ID, authority.getTenantId().longValue());
        assertTrue(authority.getRoleNames().isEmpty());
    }

    @Test
    @DisplayName("没有任何角色授予时角色为空列表，且不去查角色表")
    void noRoleGrants() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of());

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(TENANT_ID, authority.getTenantId().longValue());
        assertTrue(authority.getRoleNames().isEmpty());
        verify(roleMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("userId 为空时直接返回空归属，不查库")
    void nullUserId() {
        UserAuthority authority = service.resolve(null);

        assertNull(authority.getTenantId());
        assertTrue(authority.getRoleNames().isEmpty());
        verify(tenantUserMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("多个角色的权限合并去重，顺序按首次出现")
    void mergesPermissionsAcrossRoles() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L), grant(6L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                // 逗号后带空格 —— 即 PostgreSQL jsonb 读回时的规范化形态
                role(5L, "AUDITOR", "[\"audit_log:read\", \"report:export\"]"),
                role(6L, "VIEWER", "[\"audit_log:read\", \"data_source:read\"]")));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(List.of("AUDITOR", "VIEWER"), authority.getRoleNames());
        assertEquals(List.of("audit_log:read", "report:export", "data_source:read"),
                authority.getPermissions(),
                "被两个角色重复授予的 audit_log:read 只应出现一次，且位置在首次出现处");
    }

    @Test
    @DisplayName("权限为空数组或未配置时权限为空列表，不影响角色名")
    void emptyOrMissingPermissions() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L), grant(6L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role(5L, "AUDITOR", "[]"),
                role(6L, "VIEWER", null)));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(List.of("AUDITOR", "VIEWER"), authority.getRoleNames());
        assertTrue(authority.getPermissions().isEmpty());
    }

    @Test
    @DisplayName("permissions 不是字符串数组时按空处理，不抛异常也不污染其它角色")
    void malformedPermissionsTreatedAsEmpty() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, TENANT_ID)));
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L), grant(6L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role(5L, "AUDITOR", "{\"oops\":true}"),
                role(6L, "VIEWER", "[\"audit_log:read\"]")));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(List.of("audit_log:read"), authority.getPermissions(),
                "坏掉的配置应被跳过，而不是让整个装配失败");
    }

    @Test
    @DisplayName("resolveInTenant 按指定租户装配，不查成员关系表")
    void resolveInExplicitTenant() {
        // 注意：mock 的 selectList(any()) 不校验 wrapper 内容，
        // 所以这里不断言"查询条件里的 tenantId 是 2002" —— 那由集成测试覆盖。
        when(tenantUserRoleMapper.selectList(any())).thenReturn(List.of(grant(5L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(5L, "AUDITOR", "[\"audit_log:read\"]")));

        UserAuthority authority = service.resolveInTenant(USER_ID, 2002L);

        assertEquals(2002L, authority.getTenantId().longValue(),
                "租户应原样取自入参，而不是回退到「最早加入的租户」");
        assertEquals(List.of("AUDITOR"), authority.getRoleNames());
        assertEquals(List.of("audit_log:read"), authority.getPermissions());
        // 这是它与 resolve(userId) 的关键差别：不再经 tenant_user 推定租户
        verify(tenantUserMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("resolveInTenant 的空租户是正常形态（系统级用户），返回空归属且不查库")
    void resolveInTenantWithNullArgs() {
        UserAuthority noTenant = service.resolveInTenant(USER_ID, null);
        assertNull(noTenant.getTenantId());
        assertTrue(noTenant.getPermissions().isEmpty());

        UserAuthority noUser = service.resolveInTenant(null, TENANT_ID);
        assertNull(noUser.getTenantId());

        verify(tenantUserRoleMapper, never()).selectList(any());
        verify(roleMapper, never()).selectList(any());
    }

    private TenantUser membership(Long id, Long tenantId) {
        TenantUser membership = new TenantUser();
        membership.setId(id);
        membership.setUserId(USER_ID);
        membership.setTenantId(tenantId);
        membership.setStatus("ACTIVE");
        return membership;
    }

    private TenantUserRole grant(Long roleId) {
        TenantUserRole grant = new TenantUserRole();
        grant.setTenantId(TENANT_ID);
        grant.setUserId(USER_ID);
        grant.setRoleId(roleId);
        return grant;
    }

    private Role role(Long id, String name) {
        return role(id, name, null);
    }

    /** {@code permissions} 传的是该 JSONB 列在 Java 侧的原始字符串形态。 */
    private Role role(Long id, String name, String permissions) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setPermissions(permissions);
        role.setDeletedAt(null);
        return role;
    }
}
