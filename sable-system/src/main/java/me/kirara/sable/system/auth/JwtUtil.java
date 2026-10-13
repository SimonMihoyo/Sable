package me.kirara.sable.system.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 工具 — 负责访问令牌 / 刷新令牌的签发与校验。
 *
 * <p>算法固定为 HS256，密钥取自 {@link AuthProperties#getSecret()}。
 * 解析失败或令牌过期统一抛出 {@link BusinessException}，交由全局异常处理器转换为统一响应。</p>
 */
@Component
public class JwtUtil {

    /** 租户 ID 声明名。 */
    public static final String CLAIM_TENANT_ID = "tid";

    /** 令牌唯一标识声明名（JWT 标准声明，由 {@code Jwts.builder().id(...)} 写入）。 */
    public static final String CLAIM_TOKEN_ID = "jti";

    /** 用户名声明名。 */
    public static final String CLAIM_USERNAME = "usr";

    /** 角色标识列表声明名（角色 name，如 SUPER_ADMIN）。 */
    public static final String CLAIM_ROLES = "roles";

    /** 权限列表声明名。 */
    public static final String CLAIM_PERMISSIONS = "perm";

    /** 令牌类型声明名。 */
    public static final String CLAIM_TYPE = "typ";

    /** 访问令牌类型值。 */
    public static final String TYPE_ACCESS = "access";

    /** 刷新令牌类型值。 */
    public static final String TYPE_REFRESH = "refresh";

    /** HS256 对密钥长度的最低要求（字节）。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final AuthProperties properties;
    private final SecretKey key;

    public JwtUtil(AuthProperties properties) {
        this.properties = properties;
        this.key = buildKey(properties.getSecret());
    }

    /**
     * 签发访问令牌。
     *
     * <p>每枚令牌携带独立的 {@code jti}，让「同一用户的任意两枚令牌」都可被区分，
     * 这是登出吊销能精确到单枚令牌的前提。</p>
     *
     * <p>租户与角色写入令牌后，后续请求无需再查库即可知道"当前在哪个租户、什么角色"；
     * 代价是它们在令牌有效期内不会跟着库里的变更走，改角色需等令牌过期或强制重新登录。</p>
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param tenantId    租户 ID，可为空（无租户归属的系统级用户）
     * @param roles       角色标识列表，可为空
     * @param permissions 权限标识列表，可为空
     * @return 紧凑格式的 JWT 字符串
     */
    public String issueAccessToken(Long userId, String username, Long tenantId,
                                   List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(newTokenId())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TENANT_ID, tenantId)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * 签发刷新令牌 — 仅承载用户主体，不含业务声明。
     *
     * @param userId 用户 ID
     * @return 紧凑格式的 JWT 字符串
     */
    public String issueRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .id(newTokenId())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getRefreshTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌。
     *
     * @param token JWT 字符串
     * @return 载荷中的业务字段
     * @throws BusinessException 令牌过期（{@link ErrorCode#TOKEN_EXPIRED}）或非法（{@link ErrorCode#TOKEN_INVALID}）
     */
    public JwtPayload parse(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        JwtPayload payload = new JwtPayload();
        payload.setJti(claims.getId());
        payload.setUserId(Long.valueOf(claims.getSubject()));
        payload.setUsername(claims.get(CLAIM_USERNAME, String.class));
        payload.setTenantId(toLong(claims.get(CLAIM_TENANT_ID)));
        payload.setRoles(toStringList(claims.get(CLAIM_ROLES)));
        payload.setPermissions(toStringList(claims.get(CLAIM_PERMISSIONS)));
        payload.setType(claims.get(CLAIM_TYPE, String.class));
        payload.setExpiresAt(claims.getExpiration() == null ? null : claims.getExpiration().toInstant());
        return payload;
    }

    /** 生成一枚令牌的唯一标识。 */
    private static String newTokenId() {
        return UUID.randomUUID().toString();
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("sable.auth.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，无法用于 HS256 签名");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
