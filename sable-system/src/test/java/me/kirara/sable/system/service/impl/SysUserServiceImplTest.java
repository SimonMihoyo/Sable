package me.kirara.sable.system.service.impl;

import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 的创建用户流程测试。
 *
 * <p>核心断言只有一个：**落到 {@code password_hash} 列的值不能等于请求里的明文**。
 * 之前的实现直接把明文写进该列（`// TODO 使用 BCrypt 加密`），
 * 于是新建的用户既登不上、库里又躺着一份明文。</p>
 */
class SysUserServiceImplTest {

    private SysUserMapper sysUserMapper;
    private PasswordEncoder passwordEncoder;
    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        // 用低强度 BCrypt，测试里跑得快；与生产配置无关
        passwordEncoder = new BCryptPasswordEncoder(4);
        sysUserService = new SysUserServiceImpl(passwordEncoder);
        // ServiceImpl 的 baseMapper 是受保护字段，单测里手工注入
        ReflectionTestUtils.setField(sysUserService, "baseMapper", sysUserMapper);
    }

    private UserCreateRequest request(String username, String password) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setEmail("dave@example.com");
        request.setPhone("13900139000");
        request.setDisplayName("戴夫");
        return request;
    }

    @Test
    @DisplayName("入库的是 BCrypt 哈希，不是明文")
    void createUserHashesPassword() {
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        sysUserService.createUser(request("dave", "PlainP@ss123"));

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

        SysUser first = sysUserService.createUser(request("dave", "PlainP@ss123"));
        SysUser second = sysUserService.createUser(request("erin", "PlainP@ss123"));

        assertNotEquals(first.getPasswordHash(), second.getPasswordHash());
    }

    @Test
    @DisplayName("创建时补齐状态与风控字段默认值")
    void createUserFillDefaults() {
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        SysUser created = sysUserService.createUser(request("dave", "PlainP@ss123"));

        assertEquals("ACTIVE", created.getStatus());
        assertEquals(Boolean.FALSE, created.getMfaEnabled());
        assertEquals(0, created.getLoginFailCount().intValue());
        assertEquals("dave", created.getUsername());
        assertEquals("戴夫", created.getDisplayName());
        assertEquals("dave@example.com", created.getEmail());
        assertEquals("13900139000", created.getPhone());
    }
}
