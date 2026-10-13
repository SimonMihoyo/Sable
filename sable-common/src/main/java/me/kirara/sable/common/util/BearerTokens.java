package me.kirara.sable.common.util;

/**
 * Authorization 请求头解析工具。
 *
 * <p>认证过滤器（读令牌填上下文）与登出接口（读令牌做吊销）都需要从请求头里
 * 取出裸令牌。放在公共模块是为了让两处共用同一套判定规则，
 * 避免出现"过滤器认得、登出不认得"这类不一致。</p>
 */
public final class BearerTokens {

    /** 认证请求头名。 */
    public static final String HEADER = "Authorization";

    /** Bearer 方案前缀。 */
    private static final String PREFIX = "Bearer ";

    private BearerTokens() {
    }

    /**
     * 从 Authorization 头中取出裸令牌。
     *
     * <p>RFC 7235 规定认证方案名大小写不敏感，故 {@code bearer}/{@code BEARER} 均接受。
     * 头缺失、方案不符或令牌为空时返回 {@code null}，由调用方决定如何处理。</p>
     *
     * @param authorizationHeader 请求头原文，可为 null
     * @return 裸令牌，取不到时返回 {@code null}
     */
    public static String resolve(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.length() <= PREFIX.length()
                || !value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = value.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
