package me.kirara.sable.task;

import me.kirara.sable.system.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 令牌吊销记录清理任务。
 *
 * <p>{@code token_revocation} 只对"仍在有效期内的令牌"有意义：令牌一旦自然过期，
 * 校验阶段就会因 exp 被拒，再留着这条吊销记录只是白占空间。若不清理，
 * 表会随登出次数单调增长。</p>
 *
 * <p>执行时间由 {@code sable.auth.revocation-cleanup-cron} 配置（默认每天 03:30，
 * 避开通勤高峰）。清理失败不重试也不影响业务 —— 吊销记录多留一天无害，
 * 但异常必须记 ERROR 以便暴露问题。</p>
 */
@Component
public class TokenRevocationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationCleanupJob.class);

    private final TokenRevocationService tokenRevocationService;

    public TokenRevocationCleanupJob(TokenRevocationService tokenRevocationService) {
        this.tokenRevocationService = tokenRevocationService;
    }

    @Scheduled(cron = "${sable.auth.revocation-cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredRevocations() {
        try {
            int deleted = tokenRevocationService.purgeExpired();
            log.info("令牌吊销记录清理完成，删除 {} 条", deleted);
        } catch (Exception e) {
            // 定时任务不能因单次失败中断；这里兜住异常，保留堆栈供排查
            log.error("令牌吊销记录清理失败，将在下次调度重试", e);
        }
    }
}
