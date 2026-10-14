package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.mapper.TenantMapper;
import me.kirara.sable.system.service.TenantSchemaProvisioner;
import me.kirara.sable.system.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Tenant 业务服务实现。 */
@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantServiceImpl.class);

    /**
     * 存储侧供给能力。用 {@link ObjectProvider} 而不是直接注入：本模块的单测
     * （只加载 MyBatis 映射、不加载启动模块）里没有它的实现，构造器硬依赖会让测试跑不起来。
     */
    private final ObjectProvider<TenantSchemaProvisioner> provisionerProvider;

    public TenantServiceImpl(ObjectProvider<TenantSchemaProvisioner> provisionerProvider) {
        this.provisionerProvider = provisionerProvider;
    }

    @Override
    public Tenant create(Tenant tenant) {
        save(tenant);

        TenantSchemaProvisioner provisioner = provisionerProvider.getIfAvailable();
        if (provisioner == null) {
            // 没有存储侧实现时只落元数据。正常启动不会走到这里 —— 这条分支是为了让
            // 只加载库模块的测试仍然可用。
            log.warn("未找到 TenantSchemaProvisioner 实现，租户 {} 仅写入元数据，schema 未创建", tenant.getId());
            return tenant;
        }

        try {
            provisioner.provision(tenant.getId());
        } catch (RuntimeException e) {
            // 补偿：schema 没建起来，租户行就不能留。
            // 留着会得到一个"元数据在、业务表永远查不到"的租户，而且它占着 code 的唯一约束，
            // 用同一个 code 重试也会失败。removeById 是物理删除（本项目未启用 @TableLogic）。
            log.error("租户 {} 的 schema 供给失败，回滚元数据", tenant.getId(), e);
            removeById(tenant.getId());
            throw e;
        }
        return tenant;
    }
}
