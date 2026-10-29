package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.discovery.dto.ScheduleRequest;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.schedule.ScheduleCrons;
import me.kirara.sable.discovery.service.DataSourceService;
import me.kirara.sable.discovery.service.ScanScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时扫描调度实现。
 *
 * <p>写库一律用 {@link LambdaUpdateWrapper} 而不是"构造实体再 updateById"：
 * 关闭定时扫描时要把 {@code schedule_next_run_at} 写成 NULL，而 MyBatis-Plus 的
 * updateById 默认忽略 null 字段 —— 用实体更新会留下一个过期的下次执行时间，
 * 关掉开关看起来"没生效"。</p>
 */
@Service
public class ScanScheduleServiceImpl implements ScanScheduleService {

    private final DataSourceService dataSourceService;

    public ScanScheduleServiceImpl(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSchedule(Long sourceId, ScheduleRequest request) {
        dataSourceService.requireById(sourceId);

        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        String cron = ScheduleCrons.normalize(request.getCronExpression());
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime nextRunAt = null;
        if (enabled) {
            if (cron == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                        "启用定时扫描时必须提供 cron 表达式");
            }
            nextRunAt = ScheduleCrons.next(cron, now);
            if (nextRunAt == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                        "cron 表达式不会再触发，请检查：" + cron);
            }
        }

        LambdaUpdateWrapper<DataSource> update = new LambdaUpdateWrapper<DataSource>()
                .eq(DataSource::getId, sourceId)
                .set(DataSource::getScheduleEnabled, enabled)
                // 关闭时显式置空：留着旧的 next_run_at 会让"已关闭"看起来还在等下一次
                .set(DataSource::getScheduleNextRunAt, nextRunAt);
        if (cron != null) {
            // 关闭时不传 cron 表示"只关掉、保留原表达式"
            update.set(DataSource::getScheduleCron, cron);
        }
        dataSourceService.update(update);
    }

    @Override
    public List<DataSource> listDue(LocalDateTime now) {
        return dataSourceService.list(new LambdaQueryWrapper<DataSource>()
                .isNull(DataSource::getDeletedAt)
                .eq(DataSource::getScheduleEnabled, true)
                .isNotNull(DataSource::getScheduleNextRunAt)
                .le(DataSource::getScheduleNextRunAt, now)
                .orderByAsc(DataSource::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceNextRun(Long sourceId, LocalDateTime now) {
        DataSource source = dataSourceService.requireById(sourceId);
        String cron = source.getScheduleCron();
        if (cron == null) {
            // 理论上不可达（启用时必填）；真遇到了就把开关关掉，避免每轮空转
            dataSourceService.update(new LambdaUpdateWrapper<DataSource>()
                    .eq(DataSource::getId, sourceId)
                    .set(DataSource::getScheduleEnabled, false)
                    .set(DataSource::getScheduleNextRunAt, null));
            return;
        }
        LocalDateTime nextRunAt = ScheduleCrons.next(cron, now);
        LambdaUpdateWrapper<DataSource> update = new LambdaUpdateWrapper<DataSource>()
                .eq(DataSource::getId, sourceId)
                .set(DataSource::getScheduleNextRunAt, nextRunAt);
        if (nextRunAt == null) {
            // cron 合法但不再产生触发时间（例如只在过去的日期触发）—— 关闭，别每轮重试
            update.set(DataSource::getScheduleEnabled, false);
        }
        dataSourceService.update(update);
    }
}
