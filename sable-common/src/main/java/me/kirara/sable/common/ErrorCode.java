package me.kirara.sable.common;

public enum ErrorCode {

    // 通用 0xxxx
    SUCCESS(0, "success"),
    INTERNAL_ERROR(1, "系统内部错误"),
    PARAM_INVALID(2, "参数校验失败"),

    // 认证 40001-40099
    AUTH_FAILED(40001, "认证失败"),
    TOKEN_EXPIRED(40002, "Token已过期"),
    TOKEN_INVALID(40003, "Token无效"),
    MFA_REQUIRED(40004, "需要MFA验证"),
    MFA_INVALID(40005, "MFA验证码错误"),

    // 租户 40101-40199
    TENANT_NOT_FOUND(40101, "租户不存在"),
    TENANT_DISABLED(40102, "租户已停用"),
    TENANT_QUOTA_EXCEEDED(40103, "租户配额超限"),

    // 数据源 40201-40299
    DATASOURCE_NOT_FOUND(40201, "数据源不存在"),
    DATASOURCE_CONNECT_FAILED(40202, "数据源连接失败"),
    DATASOURCE_DUPLICATE(40203, "数据源名称重复"),

    // 分类分级 40301-40399
    CLASSIFY_RULE_NOT_FOUND(40301, "分类规则不存在"),
    CLASSIFY_RULE_CONFLICT(40302, "规则冲突"),

    // 审计 40401-40499
    AUDIT_EVENT_INVALID(40401, "审计事件格式错误"),

    // 合规评估 40501-40599
    ASSESSMENT_NOT_FOUND(40501, "评估任务不存在"),
    ASSESSMENT_ALREADY_RUNNING(40502, "评估任务正在执行中"),

    // 告警 40601-40699
    ALERT_RULE_NOT_FOUND(40601, "告警规则不存在"),

    // 报告 40701-40799
    REPORT_NOT_FOUND(40701, "报告不存在"),
    REPORT_GENERATION_FAILED(40702, "报告生成失败"),

    // 权限 40801-40899
    FORBIDDEN(40801, "无权限访问"),
    ROLE_NOT_FOUND(40802, "角色不存在"),

    // 文件 40901-40999
    FILE_NOT_FOUND(40901, "文件不存在"),
    FILE_TOO_LARGE(40902, "文件大小超限");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
