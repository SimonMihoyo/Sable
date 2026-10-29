package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.TraceContext;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.discovery.connect.JdbcConnectionFactory;
import me.kirara.sable.discovery.crypto.DataSourceCipher;
import me.kirara.sable.discovery.dto.ScanCreateRequest;
import me.kirara.sable.discovery.engine.MetadataScanEngine;
import me.kirara.sable.discovery.engine.MetadataSnapshot;
import me.kirara.sable.discovery.engine.ScanCancelledException;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.entity.ScanTask;
import me.kirara.sable.discovery.mapper.ScanTaskMapper;
import me.kirara.sable.discovery.service.AssetSyncService;
import me.kirara.sable.discovery.service.DataSourceService;
import me.kirara.sable.discovery.service.ScanTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 扫描任务实现 —— 创建、异步执行、取消。
 *
 * <h2>租户上下文为什么必须手动搬</h2>
 * <p>租户靠 {@code TenantContext}（ThreadLocal）路由连接，请求线程里有、异步线程里没有。
 * 不搬的后果不是"扫错租户"这么温和：{@code TenantRequiredInterceptor} 会直接把第一条
 * 查询打成 40104。因此 {@link #submit} 里先取出租户 ID，{@code runScan} 一进来就写回
 * 上下文，{@code finally} 里清理（线程是复用的，不清会把下一个任务带进上一个租户）。</p>
 *
 * <h2>状态机</h2>
 * <p>{@code PENDING → RUNNING → COMPLETED / FAILED / CANCELLED}。每次状态写入都带
 * "当前状态"条件（{@code WHERE status = ...}），所以取消之后执行线程的"完成/失败"
 * 不会把 CANCELLED 覆盖掉 —— 这是取消能真正生效的关键，而不是靠线程中断。</p>
 */
@Service
public class ScanTaskServiceImpl extends ServiceImpl<ScanTaskMapper, ScanTask> implements ScanTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScanTaskServiceImpl.class);

    private static final int ERROR_MESSAGE_LIMIT = 500;

    private final DataSourceService dataSourceService;

    private final DataSourceCipher cipher;

    private final JdbcConnectionFactory connectionFactory;

    private final MetadataScanEngine engine;

    private final AssetSyncService assetSyncService;

    private final Executor scanTaskExecutor;

    public ScanTaskServiceImpl(DataSourceService dataSourceService,
                               DataSourceCipher cipher,
                               JdbcConnectionFactory connectionFactory,
                               MetadataScanEngine engine,
                               AssetSyncService assetSyncService,
                               @Qualifier("scanTaskExecutor") Executor scanTaskExecutor) {
        this.dataSourceService = dataSourceService;
        this.cipher = cipher;
        this.connectionFactory = connectionFactory;
        this.engine = engine;
        this.assetSyncService = assetSyncService;
        this.scanTaskExecutor = scanTaskExecutor;
    }

    @Override
    public ScanTask submit(Long sourceId, ScanCreateRequest request) {
        DataSource source = dataSourceService.requireById(sourceId);
        String scanType = normalizeScanType(request.getScanType());
        assertNoActiveTask(sourceId);

        ScanTask task = new ScanTask();
        task.setSourceId(sourceId);
        task.setName(StringUtils.hasText(request.getName())
                ? request.getName().trim()
                : source.getName() + " 扫描");
        task.setScanType(scanType);
        task.setTriggerType(normalizeTriggerType(request.getTriggerType()));
        task.setCronExpression(request.getCronExpression());
        task.setStatus("PENDING");
        task.setCreatedBy(UserContext.getUserId());
        save(task);

        // 先取租户：请求线程里才有，异步线程里拿不到
        Long tenantId = TenantContext.requireTenantId();
        dispatch(task.getId(), tenantId, UserContext.getUserId());
        return task;
    }

    @Override
    public void cancel(Long taskId) {
        ScanTask task = getById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "扫描任务不存在");
        }
        if ("CANCELLED".equals(task.getStatus())) {
            return;
        }
        if (!"PENDING".equals(task.getStatus()) && !"RUNNING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "任务已结束，无法取消");
        }
        update(new LambdaUpdateWrapper<ScanTask>()
                .set(ScanTask::getStatus, "CANCELLED")
                .set(ScanTask::getCompletedAt, LocalDateTime.now())
                .eq(ScanTask::getId, taskId)
                .in(ScanTask::getStatus, "PENDING", "RUNNING"));
    }

    private void dispatch(Long taskId, Long tenantId, Long userId) {
        try {
            scanTaskExecutor.execute(() -> runScan(taskId, tenantId, userId));
        } catch (RejectedExecutionException e) {
            log.warn("扫描任务提交被线程池拒绝 taskId={}", taskId);
            failTask(taskId, "扫描线程池繁忙，任务未能启动");
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "扫描任务繁忙，请稍后重试");
        }
    }

    /** 异步执行体：在独立线程里跑，租户与日志上下文都必须自己带上。 */
    private void runScan(Long taskId, Long tenantId, Long userId) {
        TenantContext.setTenantId(tenantId);
        TraceContext.setTenantId(tenantId);
        if (userId != null) {
            TraceContext.setUserId(userId);
        }
        Long sourceId = null;
        try {
            if (!markRunning(taskId)) {
                log.info("扫描任务已被取消或已结束，跳过执行 taskId={}", taskId);
                return;
            }
            ScanTask task = getById(taskId);
            if (task == null) {
                return;
            }
            sourceId = task.getSourceId();
            DataSource source = dataSourceService.requireById(sourceId);
            if (!connectionFactory.isTestable(source)) {
                String reason = connectionFactory.untestableReason(source);
                failTask(taskId, reason);
                markSourceScanned(sourceId, "FAILED", reason);
                return;
            }
            String password = cipher.decrypt(source.getPasswordEnc());
            try (Connection connection = connectionFactory.open(source, password)) {
                MetadataSnapshot snapshot = engine.scan(source, connection, () -> isCancelled(taskId));
                assetSyncService.sync(sourceId, snapshot);
                completeTask(taskId, snapshot);
                markSourceScanned(sourceId, "SUCCESS", null);
                log.info("扫描完成 taskId={} sourceId={} schemas={} tables={} columns={}",
                        taskId, sourceId, snapshot.schemaCount(), snapshot.tableCount(), snapshot.columnCount());
            }
        } catch (ScanCancelledException e) {
            // 状态已由 cancel() 写为 CANCELLED，这里不再覆盖；资产可能已写入一部分
            log.info("扫描任务已取消 taskId={}", taskId);
            if (sourceId != null) {
                markSourceScanned(sourceId, "PARTIAL", null);
            }
        } catch (Exception e) {
            if (isCancelled(taskId)) {
                // 取消可能发生在"连接还没建好"的阶段：此时抛的是普通 SQLException，
                // 不是 ScanCancelledException，但结论一样 —— 是用户取消，不是扫描失败
                log.info("扫描任务已被取消（异常发生在取消之后） taskId={}", taskId);
                if (sourceId != null) {
                    markSourceScanned(sourceId, "PARTIAL", null);
                }
                return;
            }
            String message = summarize(e);
            log.error("扫描任务失败 taskId={} sourceId={}: {}", taskId, sourceId, message, e);
            failTask(taskId, message);
            if (sourceId != null) {
                markSourceScanned(sourceId, "FAILED", message);
            }
        } finally {
            TenantContext.clear();
            TraceContext.clear();
        }
    }

    /** 只把 PENDING 推进到 RUNNING；返回 false 说明任务已被取消或已被别的执行者接手。 */
    private boolean markRunning(Long taskId) {
        return update(new LambdaUpdateWrapper<ScanTask>()
                .set(ScanTask::getStatus, "RUNNING")
                .set(ScanTask::getStartedAt, LocalDateTime.now())
                .eq(ScanTask::getId, taskId)
                .eq(ScanTask::getStatus, "PENDING"));
    }

    private void completeTask(Long taskId, MetadataSnapshot snapshot) {
        boolean updated = update(new LambdaUpdateWrapper<ScanTask>()
                .set(ScanTask::getStatus, "COMPLETED")
                .set(ScanTask::getTotalSchemas, snapshot.schemaCount())
                .set(ScanTask::getTotalTables, snapshot.tableCount())
                .set(ScanTask::getTotalColumns, snapshot.columnCount())
                .set(ScanTask::getCompletedAt, LocalDateTime.now())
                .set(ScanTask::getErrorMessage, null)
                .eq(ScanTask::getId, taskId)
                .eq(ScanTask::getStatus, "RUNNING"));
        if (!updated) {
            log.info("扫描期间任务状态已变化（多为取消），完成结果不覆盖状态 taskId={}", taskId);
        }
    }

    /** 只覆盖 PENDING / RUNNING；已取消的任务保持 CANCELLED。 */
    private void failTask(Long taskId, String message) {
        update(new LambdaUpdateWrapper<ScanTask>()
                .set(ScanTask::getStatus, "FAILED")
                .set(ScanTask::getCompletedAt, LocalDateTime.now())
                .set(ScanTask::getErrorMessage, truncate(message))
                .eq(ScanTask::getId, taskId)
                .in(ScanTask::getStatus, "PENDING", "RUNNING"));
    }

    private void markSourceScanned(Long sourceId, String scanStatus, String errorMessage) {
        DataSource update = new DataSource();
        update.setId(sourceId);
        update.setLastScanAt(LocalDateTime.now());
        update.setLastScanStatus(scanStatus);
        update.setScanErrorMsg(errorMessage);
        dataSourceService.updateById(update);
    }

    private boolean isCancelled(Long taskId) {
        ScanTask current = getById(taskId);
        return current == null || "CANCELLED".equals(current.getStatus());
    }

    private void assertNoActiveTask(Long sourceId) {
        long active = count(new LambdaQueryWrapper<ScanTask>()
                .eq(ScanTask::getSourceId, sourceId)
                .in(ScanTask::getStatus, List.of("PENDING", "RUNNING")));
        if (active > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "该数据源已有扫描任务未结束，请等待完成或先取消");
        }
    }

    private static String normalizeScanType(String scanType) {
        if (!StringUtils.hasText(scanType)) {
            return "FULL";
        }
        String value = scanType.trim().toUpperCase();
        if (!"FULL".equals(value) && !"INCREMENTAL".equals(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "扫描类型只能是 FULL 或 INCREMENTAL");
        }
        return value;
    }

    private static String normalizeTriggerType(String triggerType) {
        if (!StringUtils.hasText(triggerType)) {
            return "MANUAL";
        }
        String value = triggerType.trim().toUpperCase();
        if (!List.of("MANUAL", "SCHEDULED", "API").contains(value)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "触发类型只能是 MANUAL / SCHEDULED / API");
        }
        return value;
    }

    /** 异常摘要 —— 只留一句话，避免把整段堆栈写进 error_message。 */
    private static String summarize(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = e.getClass().getSimpleName();
        }
        return truncate(message);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_LIMIT ? message : message.substring(0, ERROR_MESSAGE_LIMIT);
    }
}
