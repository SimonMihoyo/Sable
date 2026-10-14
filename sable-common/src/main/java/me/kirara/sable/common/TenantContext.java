package me.kirara.sable.common;

public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * 取当前请求的租户 ID；缺失时抛 {@link ErrorCode#TENANT_CONTEXT_MISSING}（40104）。
     *
     * <p><b>这是「当前请求该有租户、却没有」的唯一出口。</b>需要租户的调用点一律走这里，
     * 不要再各写一份 {@code getTenantId() == null} 判断 —— 各处自行判断正是错误码
     * 分叉的成因（历史上 {@code UserController} 与 {@code RoleController} 就有两份
     * 同语义实现，一份报 40101、一份报 40104）。</p>
     *
     * <p>注意与 {@link ErrorCode#TENANT_NOT_FOUND}（40101）的区别：那个表达的是
     * "你要找的租户不存在"（请求内容问题，客户端该提示用户）；本方法的 40104 表达的是
     * "请求没带租户上下文"（调用姿势问题，多半是链路漏了租户，属服务端缺陷）。</p>
     */
    public static Long requireTenantId() {
        Long tenantId = TENANT_ID.get();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.TENANT_CONTEXT_MISSING);
        }
        return tenantId;
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
