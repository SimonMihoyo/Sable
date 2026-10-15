package me.kirara.sable.common;

import java.util.List;

/**
 * 权限码字典 —— 全系统"能做什么"的唯一定义处。
 *
 * <p><b>为什么需要它：</b>权限码此前散落在三处各写一份 —— {@code V1__init_schema.sql} 里
 * {@code role.permissions} 列的注释样例、{@code db/dev/R__dev_seed.sql} 给预置角色配的
 * 实际取值、以及前端的 {@code src/access.ts}。三份手工维护的副本迟早会漂移，而漂移的表现
 * 很难看：接口上写的码不在字典里 → 没有任何角色持有它 → 那个接口**对所有人都是 403**，
 * 而日志里只有一句"无权限"，看不出是配错了。这里把它变成代码常量：接口侧只引用常量
 * （见 {@code @PreAuthorize} 的写法），并有一条测试比对种子数据里的取值是否都在本类中。</p>
 *
 * <p><b>鉴权一律按权限码判断，不按角色名判断。</b>角色只是权限的容器，且
 * {@code FR-6.2.2} 允许租户自建角色 —— 写死 {@code hasRole('SUPER_ADMIN')} 会让自建角色
 * 天然无效。因此令牌与安全上下文里携带的是权限码，角色的作用只是"把一组权限打包"。</p>
 *
 * <p><b>没有通配符。</b>本字典刻意不做 {@code *} 之类的兜底：新增一个权限码后，必须有角色
 * 被显式授予它，才会有人能访问对应接口。这是合规产品该有的方向 —— 漏配的表现是
 * 403（可发现、可修复），而不是"所有人凭空多了新权限"（不可发现）。</p>
 *
 * <p><b>三种消费者，只有第一种是安全边界：</b>服务端接口校验（{@code @PreAuthorize}）、
 * 令牌的 {@code perm} 声明、前端菜单与按钮的可见性。后两者是"携带"与"显示"，
 * 它们被绕过不构成越权；真正的拦截只发生在第一种。</p>
 */
public final class Permission {

    // ── 数据源（sable-discovery）──
    public static final String DATA_SOURCE_READ = "data_source:read";
    public static final String DATA_SOURCE_WRITE = "data_source:write";

    // ── 审计日志（sable-audit）──
    public static final String AUDIT_LOG_READ = "audit_log:read";
    public static final String AUDIT_LOG_EXPORT = "audit_log:export";

    // ── 合规评估（sable-assessment）──
    public static final String ASSESSMENT_READ = "assessment:read";
    public static final String ASSESSMENT_WRITE = "assessment:write";

    // ── 报告（sable-report）──
    public static final String REPORT_READ = "report:read";
    public static final String REPORT_EXPORT = "report:export";

    // ── 系统管理（sable-system）──
    public static final String SYSTEM_MANAGE = "system:manage";

    /**
     * 全部权限码。
     *
     * <p>目前唯一的消费者是一致性测试（比对种子数据用到的码是否都在这里），
     * 以及将来做"角色权限编辑"界面时的候选项来源 —— 到那时它才真正出现在业务代码里，
     * 现在把它列全是为了让"新增一个权限码"有一个明确的收口位置。</p>
     */
    public static final List<String> ALL = List.of(
            DATA_SOURCE_READ, DATA_SOURCE_WRITE,
            AUDIT_LOG_READ, AUDIT_LOG_EXPORT,
            ASSESSMENT_READ, ASSESSMENT_WRITE,
            REPORT_READ, REPORT_EXPORT,
            SYSTEM_MANAGE);

    private Permission() {
    }
}
