package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.entity.TenantUserRole;
import me.kirara.sable.system.mapper.SysUserMapper;
import me.kirara.sable.system.mapper.TenantUserMapper;
import me.kirara.sable.system.mapper.TenantUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 的创建用户与角色分配流程测试。
 *
 * <p>两条核心断言：</p>
 * <ul>
 *   <li><b>落到 {@code password_hash} 列的值不能等于请求里的明文</b> —— 之前的实现直接把明文
 *       写进该列（`// TODO 使用 BCrypt 加密`），于是新建的用户既登不上、库里又躺着一份明文；</li>
 *   <li><b>创建用户必须同时建立租户归属与角色授予</b> —— 少了它们，账号建出来也装配不到
 *       租户和角色，等于登不进去；{@code department} / {@code roleIds} 曾经被静默丢弃。</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
class SysUserServiceImplTest {

    private static final long TENANT_ID = 1001L;
    private static final long NEW_USER_ID = 42L;

    private SysUserMapper sysUserMapper;
    private TenantUserMapper tenantUserMapper;
    private TenantUserRoleMapper tenantUserRoleMapper;
    private PasswordEncoder passwordEncoder;
    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        // lambdaQuery 靠 TableInfo 缓存把方法引用映射成列名，该缓存只在 Spring 启动时建立
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TenantUserRole.class);

        sysUserMapper = mock(SysUserMapper.class);
        tenantUserMapper = mock(TenantUserMapper.class);
        tenantUserRoleMapper = mock(TenantUserRoleMapper.class);
        // 用低强度 BCrypt，测试里跑得快；与生产配置无关
        passwordEncoder = new BCryptPasswordEncoder(4);
        sysUserService = new SysUserServiceImpl(passwordEncoder, tenantUserMapper, tenantUserRoleMapper);
        // ServiceImpl 的 baseMapper 是受保护字段，单测里手工注入
        ReflectionTestUtils.setField(sysUserService, "baseMapper", sysUserMapper);
    }

    /** 模拟自增主键回填：真实驱动 insert 后会把生成的 id 写回实体。 */
    private void insertReturnsGeneratedId() {
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.<SysUser>getArgument(0).setId(NEW_USER_ID);
            return 1;
        });
    }

    private UserCreateRequest request(String username, String password) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail("dave@example.com");
        request.setPhone("13900139000");
        request.setDisplayName("戴夫");
        request.setDepartment("技术部");
        request.setRoleIds(List.of(5L, 6L));
        return request;
    }

    @Test
    @DisplayName("入库的是 BCrypt 哈希，不是明文")
    void createUserHashesPassword() {
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        sysUserService.createUser(TENANT_ID, request("dave", "PlainP@ss123"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser persisted = captor.getValue();

        assertNotEquals("PlainP@ss123", persisted.getPasswordHash(), "密码列不得存明文");
        assertFalse(persisted.getPasswordHash().contains("PlainP@ss123"));
        assertTrue(persisted.getPasswordHash().startsWith("$2"), "应为 BCrypt 格式: " + persisted.getPasswordHash());
        assertTrue(passwordEncoder.matches("PlainP@ss123", persisted.getPasswordHash()),
                "哈希应能被同一编码器校验回原密码");
    }

    @Test
    @DisplayName("同一密码两次创建得到不同哈希（BCrypt 自带盐）")
    void samePasswordYieldsDifferentHashes() {
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        SysUser first = sysUserService.createUser(TENANT_ID, request("dave", "PlainP@ss123"));
        SysUser second = sysUserService.createUser(TENANT_ID, request("erin", "PlainP@ss123"));

        assertNotEquals(first.getPasswordHash(), second.getPasswordHash());
    }

    @Test
    @DisplayName("创建时补齐状态与风控字段默认值")
    void createUserFillDefaults() {
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        SysUser created = sysUserService.createUser(TENANT_ID, request("dave", "PlainP@ss123"));

        assertEquals("ACTIVE", created.getStatus());
        assertEquals(Boolean.FALSE, created.getMfaEnabled());
        assertEquals(0, created.getLoginFailCount().intValue());
        assertEquals("dave", created.getUsername());
        assertEquals("戴夫", created.getDisplayName());
        assertEquals("dave@example.com", created.getEmail());
        assertEquals("13900139000", created.getPhone());
    }

    @Test
    @DisplayName("创建时建立租户归属，department 落到 tenant_user 而不是被丢掉")
    void createUserBindsTenantMembership() {
        insertReturnsGeneratedId();

        sysUserService.createUser(TENANT_ID, request("dave", "PlainP@ss123"));

        ArgumentCaptor<TenantUser> captor = ArgumentCaptor.forClass(TenantUser.class);
        verify(tenantUserMapper).insert(captor.capture());
        TenantUser membership = captor.getValue();

        assertEquals(TENANT_ID, membership.getTenantId().longValue());
        assertEquals(NEW_USER_ID, membership.getUserId().longValue(), "归属必须挂到新建的用户上");
        assertEquals("技术部", membership.getDepartment(), "department 不得被静默丢弃");
        assertEquals("ACTIVE", membership.getStatus());
    }

    @Test
    @DisplayName("roleIds 逐个落到 tenant_user_role（一个用户可持有多个角色）")
    void createUserGrantsRoles() {
        insertReturnsGeneratedId();

        sysUserService.createUser(TENANT_ID, request("dave", "PlainP@ss123"));

        ArgumentCaptor<TenantUserRole> captor = ArgumentCaptor.forClass(TenantUserRole.class);
        verify(tenantUserRoleMapper, times(2)).insert(captor.capture());

        List<TenantUserRole> grants = captor.getAllValues();
        assertEquals(List.of(5L, 6L), grants.stream().map(TenantUserRole::getRoleId).toList());
        assertTrue(grants.stream().allMatch(g -> TENANT_ID == g.getTenantId()), "授予必须落在当前租户");
        assertTrue(grants.stream().allMatch(g -> NEW_USER_ID == g.getUserId()), "授予必须挂到新建的用户上");
    }

    @Test
    @DisplayName("roleIds 为空时不写任何授予，但租户归属照常建立")
    void createUserWithoutRoles() {
        insertReturnsGeneratedId();
        UserCreateRequest withoutRoles = request("dave", "PlainP@ss123");
        withoutRoles.setRoleIds(null);

        sysUserService.createUser(TENANT_ID, withoutRoles);

        verify(tenantUserMapper).insert(any(TenantUser.class));
        verify(tenantUserRoleMapper, never()).insert(any(TenantUserRole.class));
    }

    @Test
    @DisplayName("assignRoles 是覆盖式：去重后重建，重复与 null 不会撞唯一约束")
    void assignRolesReplacesExisting() {
        sysUserService.assignRoles(TENANT_ID, NEW_USER_ID, Arrays.asList(6L, 6L, null, 7L));

        verify(tenantUserRoleMapper).delete(any());

        ArgumentCaptor<TenantUserRole> captor = ArgumentCaptor.forClass(TenantUserRole.class);
        verify(tenantUserRoleMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(6L, 7L), captor.getAllValues().stream().map(TenantUserRole::getRoleId).toList());
    }

    @Test
    @DisplayName("assignRoles 传空列表即收回该租户下的全部角色")
    void assignRolesWithEmptyListClearsAll() {
        sysUserService.assignRoles(TENANT_ID, NEW_USER_ID, List.of());

        verify(tenantUserRoleMapper).delete(any());
        verify(tenantUserRoleMapper, never()).insert(any(TenantUserRole.class));
    }
}
