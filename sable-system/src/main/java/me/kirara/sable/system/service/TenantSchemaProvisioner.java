package me.kirara.sable.system.service;

/**
 * 租户存储侧供给 —— 为租户准备它的数据库 schema 与业务表。
 *
 * <h2>为什么是一个接口</h2>
 * <p>"建租户"这件事横跨两层：<b>元数据</b>（{@code public.tenant} 里插一行）属于本模块，
 * <b>存储</b>（建 schema、跑租户级迁移）需要 Flyway 与数据源，只存在于启动模块。
 * 依赖方向是 {@code sable-app → sable-system}，因此把能力定义在库模块、由启动模块实现，
 * 避免库模块反向依赖启动模块，也避免把 Flyway 拖进每个库模块的依赖里。</p>
 *
 * <p>实现见 {@code me.kirara.sable.tenant.TenantSchemaProvisionerImpl}。
 * 找不到实现时（例如只加载库模块的切片测试）调用方按"不供给"处理，
 * 见 {@link TenantServiceImpl#create} 里的说明。</p>
 */
public interface TenantSchemaProvisioner {

    /**
     * 确保该租户的 schema 与业务表就绪。**必须幂等** —— 建租户时调用一次，
     * 启动期补齐可能再调用若干次。
     *
     * @param tenantId 租户 ID
     * @throws RuntimeException 供给失败；调用方负责决定如何补偿
     */
    void provision(Long tenantId);
}
