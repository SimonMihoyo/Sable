package me.kirara.sable.system.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.kirara.sable.system.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserResponse} 脱敏行为测试。
 *
 * <p>这里刻意用**序列化后的 JSON 字符串**做断言，而不是逐字段 getter ——
 * 要防的是"凭证跟着响应出网"，只有真正序列化一次才说明问题。</p>
 */
class UserResponseTest {

    /** 注册 classpath 上的全部模块（其中 Jackson 的 JavaTimeModule 用于 LocalDateTime）。 */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /** 造一个所有字段都填满的用户，含两处凭证。 */
    private SysUser fullUser() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("alice");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setEmail("alice@example.com");
        user.setPhone("13800138000");
        user.setDisplayName("爱丽丝");
        user.setAvatarUrl("https://example.com/a.png");
        user.setStatus("ACTIVE");
        user.setMfaEnabled(true);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        user.setLastLoginAt(LocalDateTime.of(2026, 10, 12, 21, 0));
        user.setLastLoginIp("10.0.0.1");
        user.setLoginFailCount(3);
        user.setLockedUntil(LocalDateTime.of(2026, 10, 12, 21, 15));
        user.setPasswordChangedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        user.setDeletedAt(LocalDateTime.of(2026, 10, 2, 9, 0));
        user.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        user.setUpdatedAt(LocalDateTime.of(2026, 10, 12, 21, 0));
        return user;
    }

    @Test
    @DisplayName("投影后的 JSON 不含任何凭证字段")
    void credentialsNeverSerialized() throws Exception {
        String json = objectMapper.writeValueAsString(UserResponse.from(fullUser()));

        assertFalse(json.contains("passwordHash"), "响应不得含 passwordHash: " + json);
        assertFalse(json.contains("mfaSecret"), "响应不得含 mfaSecret: " + json);
        assertFalse(json.contains("$2a$10$abcdefghijklmnopqrstuv"), "响应不得含 BCrypt 哈希明文");
        assertFalse(json.contains("JBSWY3DPEHPK3PXP"), "响应不得含 TOTP 密钥明文");
    }

    @Test
    @DisplayName("投影后的 JSON 不含其它内部字段（登录 IP / 失败次数 / 软删除标记等）")
    void internalFieldsNotSerialized() throws Exception {
        String json = objectMapper.writeValueAsString(UserResponse.from(fullUser()));

        assertFalse(json.contains("lastLoginIp"), "响应不得含 lastLoginIp: " + json);
        assertFalse(json.contains("loginFailCount"), "响应不得含 loginFailCount: " + json);
        assertFalse(json.contains("passwordChangedAt"), "响应不得含 passwordChangedAt: " + json);
        assertFalse(json.contains("deletedAt"), "响应不得含 deletedAt: " + json);
        assertFalse(json.contains("10.0.0.1"), "响应不得含登录 IP 的值");
    }

    @Test
    @DisplayName("投影保留对外字段，且值与实体一致")
    void whitelistedFieldsAreCopied() throws Exception {
        UserResponse response = UserResponse.from(fullUser());

        assertEquals(42L, response.getId().longValue());
        assertEquals("alice", response.getUsername());
        assertEquals("爱丽丝", response.getDisplayName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("13800138000", response.getPhone());
        assertEquals("https://example.com/a.png", response.getAvatarUrl());
        assertEquals("ACTIVE", response.getStatus());
        assertTrue(response.getMfaEnabled());
        assertNotNull(response.getLastLoginAt());
        assertNotNull(response.getLockedUntil());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"username\":\"alice\""), json);
    }

    @Test
    @DisplayName("实体为 null 时返回 null，便于直接透传")
    void fromNullReturnsNull() {
        assertNull(UserResponse.from(null));
    }

    @Test
    @DisplayName("批量投影逐条脱敏；入参为 null 时返回空列表而不是 NPE")
    void fromAllHandlesList() {
        List<UserResponse> list = UserResponse.fromAll(List.of(fullUser(), fullUser()));
        assertEquals(2, list.size());
        assertEquals("alice", list.get(0).getUsername());

        assertTrue(UserResponse.fromAll(null).isEmpty());
        assertTrue(UserResponse.fromAll(List.of()).isEmpty());
    }
}
