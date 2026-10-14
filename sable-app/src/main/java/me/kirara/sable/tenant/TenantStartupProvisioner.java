package me.kirara.sable.tenant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.service.TenantSchemaProvisioner;
import me.kirara.sable.system.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动期补齐租户 schema —— 由 {@code sable.tenant.provision-on-startup} 控制，默认关。
 *
 * <p>存在的理由只有一个：**开发库里已经有了租户行，但 schema 是随建租户动作产生的**。
 * 种子数据（{@code db/dev/R__dev_seed.sql}）只写公共表，于是本地库重启后需要有人替它把
 * {@code tenant_1} 建出来，否则一访问业务接口就报 relation 不存在。把这件事放在启动期，
 * 开发同学就不必手工建。</p>
 *
 * <p>生产环境默认关掉：表结构变更应当由发布流程显式触发，而不是每次重启都跑一遍 DDL。
 * 但打开它也是安全的 —— Flyway 迁移是幂等的，且它只补"缺的"，不会改已有的。</p>
 *
 * <p>单个租户失败**不会**中断启动：日志里留下 ERROR，服务照常起（否则一个租户的历史数据问题
 * 会把整个服务拖住）。该租户真正被访问时仍会明确报错，不会静默。</p>
 */
@Component
public class TenantStartupProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantStartupProvisioner.class);

    private final TenantSchemaProperties properties;
    private final TenantService tenantService;
    private final TenantSchemaProvisioner provisioner;

    public TenantStartupProvisioner(TenantSchemaProperties properties,
                                    TenantService tenantService,
                                    TenantSchemaProvisioner provisioner) {
        this.properties = properties;
        this.tenantService = tenantService;
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isProvisionOnStartup()) {
            return;
        }
        // 未删除的租户才需要 schema；TenantMapper 属于 system 包，本来就允许无租户上下文执行
        List<Tenant> tenants = tenantService.list(
                Wrappers.<Tenant>lambdaQuery().isNull(Tenant::getDeletedAt));

        int ok = 0;
        int failed = 0;
        for (Tenant tenant : tenants) {
            try {
                provisioner.provision(tenant.getId());
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.error("启动期为租户 {}（{}）补齐 schema 失败", tenant.getId(), tenant.getCode(), e);
            }
        }
        if (!tenants.isEmpty()) {
            log.info("启动期租户 schema 补齐完成：成功 {}，失败 {}，共 {}", ok, failed, tenants.size());
        }
    }
}
