package me.kirara.sable.tenant;

/**
 * 租户 schema 的命名规则 —— 全项目唯一一处把"租户 ID"翻成"schema 名"的地方。
 *
 * <p>之所以收敛成一个工具类而不是各处拼字符串：schema 名会被直接拼进 SQL 与 JDBC 连接参数，
 * 一旦某处写成别的形状（少个下划线、用了 code、大小写不一致），后果不是编译错误，
 * 而是**跑到另一个 schema 里读写**。集中在一处便于审查，也便于将来换命名规则时只改一个点。</p>
 *
 * <p>当前规则：{@code tenant_}{<租户 ID>}，例如 1 号租户是 {@code tenant_1}。
 * 刻意使用**数字 ID 而不是租户 code**：code 是用户可改的业务字段，schema 名一改就得
 * 迁移整库数据；ID 由数据库生成且永不变更。</p>
 */
public final class TenantSchemas {

    /** schema 名前缀。 */
    public static final String PREFIX = "tenant_";

    /** 公共 schema —— 9 张系统表所在。 */
    public static final String PUBLIC = "public";

    private TenantSchemas() {
    }

    /**
     * 求出某租户的 schema 名。
     *
     * @param tenantId 租户 ID，必须为正数
     * @return 形如 {@code tenant_1} 的 schema 名
     */
    public static String schemaName(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("租户 ID 必须为正数，实际为 " + tenantId);
        }
        return PREFIX + tenantId;
    }

    /**
     * 租户连接使用的 search_path：租户 schema 在前，public 在后。
     *
     * <p>顺序不能反 —— 公共表在 public、业务表在租户 schema，两者都要能解析到；
     * 而"业务表优先落在本租户"这条要由租户 schema 排第一位来保证。</p>
     */
    public static String searchPath(Long tenantId) {
        return schemaName(tenantId) + "," + PUBLIC;
    }
}
