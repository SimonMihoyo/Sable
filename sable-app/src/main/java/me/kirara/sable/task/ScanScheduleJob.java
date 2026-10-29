package me.kirara.sable.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.TraceContext;
import me.kirara.sable.discovery.dto.ScanCreateRequest;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.service.ScanScheduleService;
import me.kirara.sable.discovery.service.ScanTaskService;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.service.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时扫描调度任务 —— 每 {@code sable.discovery.schedule.poll-interval} 毫秒扫一次"到期"的数据源。
 *
 * <h2>为什么是轮询而不是动态注册 cron</h2>
 * <p>动态注册（按每个数据源的 cron 往 {@code ThreadPoolTaskScheduler} 里塞任务）精度更高，
 * 但要在启动期加载全部租户的配置、并在每次改配置时反注册旧任务；多实例部署时还得处理
 * "两个实例各注册一份"。轮询模型只有一句 SQL
 * （{@code schedule_enabled AND schedule_next_run_at <= now}），代价是触发精度等于轮询间隔
 * （默认 30 秒）—— 对"每天扫一次"这类配置完全够用。</p>
 *
 * <h2>租户上下文</h2>
 * <p>调度线程没有请求上下文。这里按租户逐个切换 {@link TenantContext}，否则
 * {@code TenantRequiredInterceptor} 会把第一条业务查询打成 40104；切换后必须清理，
 * 否则下一次循环会带着上一个租户的身份去查（串租户的经典成因）。</p>
 *
 * <h2>单实例假设</h2>
 * <p>多实例部署时两个进程可能同时看到同一个到期数据源，于是提交两次 —— 第二次会被
 * {@code ScanTaskService#submit} 的"同一数据源只允许一个未结束任务"挡掉，不会真的并发扫。
 * 也就是说这里依赖任务级的互斥兜底，而不是分布式锁；真要上多实例，再补
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} 或领导者选举。</p>
 */
@Component
@ConditionalOnProperty(name = "sable.discovery.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class ScanScheduleJob {

    private static final Logger log = LoggerFactory.getLogger(ScanScheduleJob.class);

    private final TenantService tenantService;

    private final ScanScheduleService scanScheduleService;

    private final ScanTaskService scanTaskService;

    public ScanScheduleJob(TenantService tenantService,
                           ScanScheduleService scanScheduleService,
                           ScanTaskService scanTaskService) {
        this.tenantService = tenantService;
        this.scanScheduleService = scanScheduleService;
        this.scanTaskService = scanTaskService;
    }

    @Scheduled(fixedDelayString = "${sable.discovery.schedule.poll-interval:30000}",
            initialDelayString = "${sable.discovery.schedule.initial-delay:30000}")
    public void poll() {
        LocalDateTime now = LocalDateTime.now();
        // 只调度 ACTIVE 租户：停用/过期的租户不该再产生扫描流量
        List<Tenant> tenants = tenantService.list(Wrappers.<Tenant>lambdaQuery()
                .isNull(Tenant::getDeletedAt)
                .eq(Tenant::getStatus, "ACTIVE"));
        for (Tenant tenant : tenants) {
            TenantContext.setTenantId(tenant.getId());
            TraceContext.setTenantId(tenant.getId());
            try {
                pollTenant(now);
            } catch (RuntimeException e) {
                // 单个租户失败不能影响其他租户的调度
                log.error("租户 {} 的定时扫描调度失败", tenant.getId(), e);
            } finally {
                TenantContext.clear();
                TraceContext.clear();
            }
        }
    }

    private void pollTenant(LocalDateTime now) {
        for (DataSource source : scanScheduleService.listDue(now)) {
            // 先推进下次执行时间，再提交：提交失败（如已有任务在跑）也不会在同一分钟内反复重试
            scanScheduleService.advanceNextRun(source.getId(), now);
            submit(source);
        }
    }

    private void submit(DataSource source) {
        ScanCreateRequest request = new ScanCreateRequest();
        request.setTriggerType("SCHEDULED");
        request.setScanType("FULL");
        try {
            scanTaskService.submit(source.getId(), request);
            log.info("定时扫描已提交 sourceId={} name={} cron={}",
                    source.getId(), source.getName(), source.getScheduleCron());
        } catch (BusinessException e) {
            // 典型场景：上一次扫描还在跑（连接慢、表多），这一轮跳过即可，不是故障
            log.warn("定时扫描跳过 sourceId={}: {}", source.getId(), e.getMessage());
        }
    }
}
