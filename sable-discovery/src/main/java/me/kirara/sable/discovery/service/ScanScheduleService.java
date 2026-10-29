package me.kirara.sable.discovery.service;

import me.kirara.sable.discovery.dto.ScheduleRequest;
import me.kirara.sable.discovery.entity.DataSource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时扫描调度 —— 保存配置、查到期数据源、推进下次执行时间。
 *
 * <p>调度配置存在 {@code data_source} 上（每数据源最多一个），采用"下次执行时间"模型：
 * 保存时算出 {@code schedule_next_run_at}，接下来只看 {@code schedule_enabled AND
 * schedule_next_run_at <= now}。好处是停机期间错过的槽位只会补一次，而不是回放 N 次。</p>
 */
public interface ScanScheduleService {

    /**
     * 保存定时扫描配置。
     *
     * <p>启用时必须提供合法 cron；关闭时可以不传 cron（保留原表达式，只清空下次执行时间）。
     * cron 统一存成 6 段（5 段输入会被补上秒位）。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）或 cron 非法 / 启用时为空（2）
     */
    void updateSchedule(Long sourceId, ScheduleRequest request);

    /** 当前租户下已到期（含已错过）的定时扫描数据源。 */
    List<DataSource> listDue(LocalDateTime now);

    /**
     * 推进某数据源的下次执行时间。
     *
     * <p>由调度任务在**提交扫描之前**调用：先推进再提交，即使提交失败（例如已有任务在跑）
     * 也不会在同一分钟内反复重试；配合"一次错过只补一次"的模型，下一次仍是正常槽位。</p>
     */
    void advanceNextRun(Long sourceId, LocalDateTime now);
}
