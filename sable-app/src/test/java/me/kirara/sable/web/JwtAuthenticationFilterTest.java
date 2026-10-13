package me.kirara.sable.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.system.auth.AuthProperties;
import me.kirara.sable.system.auth.JwtPayload;
import me.kirara.sable.system.auth.JwtUtil;
import me.kirara.sable.system.service.TokenRevocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthenticationFilter} 单元测试 — 用 Mock 请求 / 响应驱动，不启动 Spring 容器。
 *
 * <p>覆盖的是"能不能进"这件事：白名单放行、缺失 / 过期 / 非法 / 类型不符 / 已吊销的令牌
 * 一律 401，合法令牌放行并正确填充上下文。</p>
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long";
    private static final String PROTECTED_URI = "/system/roles";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthProperties authProperties;
    private JwtUtil jwtUtil;
    private TokenRevocationService tokenRevocationService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setSecret(SECRET);
        jwtUtil = new JwtUtil(authProperties);
        tokenRevocationService = mock(TokenRevocationService.class);
        filter = new JwtAuthenticationFilter(jwtUtil, tokenRevocationService, authProperties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ 白名单

    @Test
    @DisplayName("白名单路径不携带令牌也放行")
    void whitelistedPathPassesWithoutToken() throws Exception {
        MockHttpServletRequest request = request("POST", "/auth/login", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};

        filter.doFilter(request, response, chain(reached));

        assertTrue(reached[0], "白名单路径应进入后续链路");
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    @DisplayName("登出在白名单内 —— 令牌过期后客户端仍能调用它做清理")
    void logoutIsWhitelisted() throws Exception {
        MockHttpServletRequest request = request("POST", "/auth/logout", "Bearer " + expiredToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};

        filter.doFilter(request, response, chain(reached));

        assertTrue(reached[0]);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    // ------------------------------------------------------------------ 拒绝

    @Test
    @DisplayName("受保护路径缺少令牌时返回 401 + Token无效")
    void missingTokenIsRejected() throws Exception {
        MockHttpServletResponse response = assertRejected(request("GET", PROTECTED_URI, null),
                ErrorCode.TOKEN_INVALID.getCode());
        assertEquals("缺少访问令牌", body(response).get("message").asText());
    }

    @Test
    @DisplayName("令牌已过期时返回 401 + Token已过期")
    void expiredTokenIsRejected() throws Exception {
        assertRejected(request("GET", PROTECTED_URI, "Bearer " + expiredToken()),
                ErrorCode.TOKEN_EXPIRED.getCode());
    }

    @Test
    @DisplayName("令牌被篡改时返回 401 + Token无效")
    void tamperedTokenIsRejected() throws Exception {
        String valid = jwtUtil.issueAccessToken(1L, "alice", null, List.of(), List.of());

        assertRejected(request("GET", PROTECTED_URI, "Bearer " + valid + "x"),
                ErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("用刷新令牌访问业务接口时返回 401 + Token无效")
    void refreshTokenCannotAccessBusinessApi() throws Exception {
        assertRejected(request("GET", PROTECTED_URI, "Bearer " + jwtUtil.issueRefreshToken(1L)),
                ErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("已被吊销的令牌返回 401 + Token无效")
    void revokedTokenIsRejected() throws Exception {
        String token = jwtUtil.issueAccessToken(1L, "alice", null, List.of(), List.of());
        when(tokenRevocationService.isRevoked(jwtUtil.parse(token).getJti())).thenReturn(true);

        MockHttpServletResponse response = assertRejected(request("GET", PROTECTED_URI, "Bearer " + token),
                ErrorCode.TOKEN_INVALID.getCode());
        assertEquals("令牌已被吊销", body(response).get("message").asText());
    }

    @Test
    @DisplayName("Authorization 头方案不对（非 Bearer）时按缺少令牌处理")
    void nonBearerSchemeIsRejected() throws Exception {
        MockHttpServletResponse response = assertRejected(request("GET", PROTECTED_URI, "Basic abc"),
                ErrorCode.TOKEN_INVALID.getCode());
        assertEquals("缺少访问令牌", body(response).get("message").asText());
    }

    // ------------------------------------------------------------------ 放行与上下文

    @Test
    @DisplayName("合法令牌放行，身份与令牌中的租户在链路内可读，链路结束后被清理")
    void validTokenFillsContext() throws Exception {
        String token = jwtUtil.issueAccessToken(42L, "alice", 1001L, List.of("AUDITOR"), List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        Long[] seenUserId = new Long[1];
        String[] seenUsername = new String[1];
        Long[] seenTenantId = new Long[1];
        filter.doFilter(request("GET", PROTECTED_URI, "Bearer " + token), response,
                (req, res) -> {
                    seenUserId[0] = UserContext.getUserId();
                    seenUsername[0] = UserContext.getUsername();
                    seenTenantId[0] = TenantContext.getTenantId();
                });

        assertEquals(42L, seenUserId[0].longValue());
        assertEquals("alice", seenUsername[0]);
        assertEquals(1001L, seenTenantId[0].longValue());
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());

        // 线程会被复用，请求结束后上下文必须为空
        assertNull(UserContext.getUserId());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    @DisplayName("令牌未携带租户（系统级用户）时不写入租户上下文")
    void tokenWithoutTenantLeavesTenantUnset() throws Exception {
        String token = jwtUtil.issueAccessToken(1L, "ops", null, List.of(), List.of());

        Long[] seenTenantId = {-1L};
        filter.doFilter(request("GET", PROTECTED_URI, "Bearer " + token),
                new MockHttpServletResponse(), (req, res) -> seenTenantId[0] = TenantContext.getTenantId());

        assertNull(seenTenantId[0]);
    }

    @Test
    @DisplayName("Bearer 前缀大小写不敏感")
    void bearerPrefixIsCaseInsensitive() throws Exception {
        String token = jwtUtil.issueAccessToken(1L, "alice", null, List.of(), List.of());
        boolean[] reached = {false};

        filter.doFilter(request("GET", PROTECTED_URI, "bearer " + token),
                new MockHttpServletResponse(), chain(reached));

        assertTrue(reached[0]);
    }

    // ------------------------------------------------------------------ 辅助

    /** 断言被拒：返回 401、未进入链路，并返回响应体。 */
    private MockHttpServletResponse assertRejected(MockHttpServletRequest request, int expectedCode)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};

        filter.doFilter(request, response, chain(reached));

        assertFalse(reached[0], "认证失败不应进入后续链路");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(expectedCode, body(response).get("code").asInt());
        assertTrue(response.getContentType().contains("UTF-8"), "响应需显式声明 UTF-8：" + response.getContentType());
        return response;
    }

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private String expiredToken() {
        AuthProperties expired = new AuthProperties();
        expired.setSecret(SECRET);
        expired.setAccessTokenTtl(Duration.ofSeconds(-10));
        return new JwtUtil(expired).issueAccessToken(1L, "alice", null, List.of(), List.of());
    }

    private MockHttpServletRequest request(String method, String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (authorization != null) {
            request.addHeader(JwtAuthenticationFilter.AUTH_HEADER, authorization);
        }
        return request;
    }

    private FilterChain chain(boolean[] reached) {
        return (req, res) -> reached[0] = true;
    }
}
