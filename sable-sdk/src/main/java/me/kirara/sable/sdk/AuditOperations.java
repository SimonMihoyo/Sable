package me.kirara.sable.sdk;

/** 审计操作类型常量。 */
public final class AuditOperations {

    public static final String QUERY = "QUERY";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String EXPORT = "EXPORT";
    public static final String SHARE = "SHARE";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String CONFIG_CHANGE = "CONFIG_CHANGE";
    public static final String DATA_ACCESS = "DATA_ACCESS";

    private AuditOperations() {
    }
}
