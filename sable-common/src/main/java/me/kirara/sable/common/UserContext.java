package me.kirara.sable.common;

/**
 * 当前登录用户上下文 — 由认证过滤器在请求进入时写入，请求结束时清理。
 *
 * <p>与 {@link TenantContext} 的分工：租户归属仍由 {@code TenantContext} 承载，
 * 本类只保存"谁在调用"（用户身份），两者互不重复。</p>
 *
 * <p>注意：仅保存请求级身份，<b>不做任何鉴权判断</b>。是否允许访问某个接口
 * 需要额外的权限校验，目前尚未实现。</p>
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    /** 清理上下文，避免线程复用导致的身份串号。 */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
