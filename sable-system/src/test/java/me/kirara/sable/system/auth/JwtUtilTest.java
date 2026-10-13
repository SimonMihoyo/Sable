package me.kirara.sable.system.auth;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtUtil} 单元测试 — 纯逻辑，不依赖 Spring 容器与数据库。
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long";

    private AuthProperties properties() {
        AuthProperties properties = new AuthProperties();
        properties.setSecret(SECRET);
        return properties;
    }

    private JwtUtil jwtUtil() {
        return new JwtUtil(properties());
    }

    @Test
    @DisplayName("访问令牌签发后可解析回原始声明")
    void accessTokenRoundTrip() {
        JwtUtil jwtUtil = jwtUtil();

        String token = jwtUtil.issueAccessToken(42L, "alice", 7L,
                List.of("AUDITOR"), List.of("user:read", "role:write"));
        JwtPayload payload = jwtUtil.parse(token);

        assertEquals(42L, payload.getUserId().longValue());
        assertEquals("alice", payload.getUsername());
        assertEquals(7L, payload.getTenantId().longValue());
        assertEquals(List.of("AUDITOR"), payload.getRoles());
        assertEquals(List.of("user:read", "role:write"), payload.getPermissions());
        assertEquals(JwtUtil.TYPE_ACCESS, payload.getType());
    }

    @Test
    @DisplayName("租户为空（系统级用户）时可正常签发与解析")
    void accessTokenWithoutTenant() {
        JwtUtil jwtUtil = jwtUtil();

        JwtPayload payload = jwtUtil.parse(
                jwtUtil.issueAccessToken(1L, "root", null, List.of(), List.of()));

        assertEquals(1L, payload.getUserId().longValue());
        assertNull(payload.getTenantId());
        assertTrue(payload.getRoles().isEmpty());
        assertTrue(payload.getPermissions().isEmpty());
    }

    @Test
    @DisplayName("刷新令牌类型标记为 refresh")
    void refreshTokenType() {
        JwtUtil jwtUtil = jwtUtil();

        JwtPayload payload = jwtUtil.parse(jwtUtil.issueRefreshToken(99L));

        assertEquals(99L, payload.getUserId().longValue());
        assertEquals(JwtUtil.TYPE_REFRESH, payload.getType());
    }

    @Test
    @DisplayName("同一用户连续签发的访问令牌 jti 互不相同")
    void accessTokenIdsAreUnique() {
        JwtUtil jwtUtil = jwtUtil();

        JwtPayload first = jwtUtil.parse(jwtUtil.issueAccessToken(42L, "alice", null, List.of(), List.of()));
        JwtPayload second = jwtUtil.parse(jwtUtil.issueAccessToken(42L, "alice", null, List.of(), List.of()));

        assertNotNull(first.getJti());
        assertFalse(first.getJti().isBlank(), "jti 不应为空串");
        assertNotEquals(first.getJti(), second.getJti(),
                "同一秒内签发的两枚令牌必须可区分，否则吊销会误伤");
    }

    @Test
    @DisplayName("刷新令牌同样带独立的 jti")
    void refreshTokenIdsAreUnique() {
        JwtUtil jwtUtil = jwtUtil();

        String first = jwtUtil.parse(jwtUtil.issueRefreshToken(1L)).getJti();
        String second = jwtUtil.parse(jwtUtil.issueRefreshToken(1L)).getJti();

        assertNotNull(first);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("过期令牌解析时抛出 TOKEN_EXPIRED")
    void expiredToken() {
        AuthProperties properties = properties();
        properties.setAccessTokenTtl(Duration.ofSeconds(-10));
        JwtUtil jwtUtil = new JwtUtil(properties);

        String token = jwtUtil.issueAccessToken(1L, "bob", null, List.of(), List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil.parse(token));

        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("被篡改的令牌解析时抛出 TOKEN_INVALID")
    void tamperedToken() {
        JwtUtil jwtUtil = jwtUtil();

        String token = jwtUtil.issueAccessToken(1L, "bob", null, List.of(), List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil.parse(token + "x"));

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("使用不同密钥签发的令牌无法通过校验")
    void tokenFromAnotherKey() {
        AuthProperties otherProperties = new AuthProperties();
        otherProperties.setSecret("another-secret-key-also-at-least-32-bytes-long");
        String foreignToken = new JwtUtil(otherProperties).issueAccessToken(1L, "bob", null, List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtil().parse(foreignToken));

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("密钥长度不足 32 字节时初始化即失败")
    void shortSecretRejected() {
        AuthProperties properties = new AuthProperties();
        properties.setSecret("too-short");

        assertThrows(IllegalStateException.class, () -> new JwtUtil(properties));
    }
}
