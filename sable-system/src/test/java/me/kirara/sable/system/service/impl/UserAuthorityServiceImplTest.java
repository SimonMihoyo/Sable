package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import me.kirara.sable.system.auth.UserAuthority;
import me.kirara.sable.system.entity.Role;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.mapper.RoleMapper;
import me.kirara.sable.system.mapper.TenantUserMapper;
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
 * <p>Mapper 用 Mockito 模拟，被验证的是"归属关系的选取与降级策略"。</p>
 */
@SuppressWarnings("unchecked")
class UserAuthorityServiceImplTest {

    private static final long USER_ID = 42L;

    private TenantUserMapper tenantUserMapper;
    private RoleMapper roleMapper;
    private UserAuthorityServiceImpl service;

    @BeforeEach
    void setUp() {
        // lambdaQuery 靠 TableInfo 缓存把方法引用映射成列名，该缓存只在 Spring 启动时建立
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TenantUser.class);
        TableInfoHelper.initTableInfo(assistant, Role.class);

        tenantUserMapper = mock(TenantUserMapper.class);
        roleMapper = mock(RoleMapper.class);
        service = new UserAuthorityServiceImpl(tenantUserMapper, roleMapper);
    }

    @Test
    @DisplayName("单个租户归属时装配租户与角色名")
    void singleMembership() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, 1001L, 5L)));
        when(roleMapper.selectOne(any())).thenReturn(role("COMPLIANCE_OFFICER"));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(1001L, authority.getTenantId().longValue());
        assertEquals(List.of("COMPLIANCE_OFFICER"), authority.getRoleNames());
    }

    @Test
    @DisplayName("无租户归属（系统级用户）时租户为空、角色为空列表")
    void noMembership() {
        when(tenantUserMapper.selectList(any())).thenReturn(List.of());

        UserAuthority authority = service.resolve(USER_ID);

        assertNull(authority.getTenantId());
        assertTrue(authority.getRoleNames().isEmpty());
        verify(roleMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("属于多个租户时取最早加入的一个，并把排序下推到 SQL")
    void multipleMemberships() {
        when(tenantUserMapper.selectList(any()))
                .thenReturn(List.of(membership(7L, 1001L, 5L), membership(8L, 1002L, 6L)));
        when(roleMapper.selectOne(any())).thenReturn(role("VIEWER"));

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(1001L, authority.getTenantId().longValue(),
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
        when(tenantUserMapper.selectList(any())).thenReturn(List.of(membership(7L, 1001L, 5L)));
        when(roleMapper.selectOne(any())).thenReturn(null);

        UserAuthority authority = service.resolve(USER_ID);

        assertEquals(1001L, authority.getTenantId().longValue());
        assertTrue(authority.getRoleNames().isEmpty());
    }

    @Test
    @DisplayName("userId 为空时直接返回空归属，不查库")
    void nullUserId() {
        UserAuthority authority = service.resolve(null);

        assertNull(authority.getTenantId());
        assertTrue(authority.getRoleNames().isEmpty());
        verify(tenantUserMapper, never()).selectList(any());
    }

    private TenantUser membership(Long id, Long tenantId, Long roleId) {
        TenantUser membership = new TenantUser();
        membership.setId(id);
        membership.setUserId(USER_ID);
        membership.setTenantId(tenantId);
        membership.setRoleId(roleId);
        membership.setStatus("ACTIVE");
        return membership;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(5L);
        role.setName(name);
        role.setDeletedAt(null);
        return role;
    }
}
