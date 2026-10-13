package me.kirara.sable.system.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证配置 — 绑定 {@code sable.auth.*}。
 *
 * <p>密钥 {@link #secret} 用于 HS256 签名，长度不得少于 32 字节（256 位）。
 * 长度不足时 {@link JwtUtil} 在初始化阶段即抛异常，以尽早暴露配置问题。
 * 生产环境务必通过环境变量注入，不要使用默认值。</p>
 */
@ConfigurationProperties(prefix = "sable.auth")
public class AuthProperties {

    /** HS256 签名密钥，至少 32 字节。 */
    private String secret = "sable-dev-secret-change-me-please-32bytes-min";

    /** 访问令牌有效期。 */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** 刷新令牌有效期。 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** BCrypt 强度（取值 4 ~ 31）。 */
    private int bcryptStrength = 10;

    /** 令牌签发者标识。 */
    private String issuer = "sable";

    /** 连续登录失败达到该次数后锁定账号。 */
    private int maxLoginFail = 5;

    /** 账号锁定时长（自最后一次失败起算）。 */
    private Duration lockDuration = Duration.ofMinutes(15);

    /** 改密时禁止重复使用的近期密码条数（对应 API 文档「不能与最近 3 次密码相同」）。 */
    private int passwordHistorySize = 3;

    /**
     * 免认证路径白名单（Ant 风格，如 {@code /auth/**}）。
     *
     * <p>只有确实无法携带令牌的入口才该进白名单：认证入口本身（登录 / 刷新 / 登出）
     * 与容器内部转发的错误页。放行登出是因为它按提交的令牌做吊销，
     * 令牌无效时就是个空操作 —— 若要求先通过认证，客户端在令牌过期后将无法登出。</p>
     *
     * <p>这份默认值会被 {@code application.yml} 中的 {@code sable.auth.whitelist} 整体覆盖，
     * 改配置时记得把需要的项都列全。</p>
     */
    private List<String> whitelist = new ArrayList<>(List.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/error",
            "/favicon.ico"));

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getMaxLoginFail() {
        return maxLoginFail;
    }

    public void setMaxLoginFail(int maxLoginFail) {
        this.maxLoginFail = maxLoginFail;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public int getPasswordHistorySize() {
        return passwordHistorySize;
    }

    public void setPasswordHistorySize(int passwordHistorySize) {
        this.passwordHistorySize = passwordHistorySize;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist == null ? new ArrayList<>() : whitelist;
    }
}
