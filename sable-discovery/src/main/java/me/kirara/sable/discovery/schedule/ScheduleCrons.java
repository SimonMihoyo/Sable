package me.kirara.sable.discovery.schedule;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 定时扫描的 cron 处理 —— 归一、校验、算下一次触发时间。
 *
 * <p>只做纯计算，不碰数据库，方便单测。</p>
 *
 * <p><b>为什么接受 5 段：</b>Spring 的 {@link CronExpression} 要求 6 段（秒 分 时 日 月 周），
 * 但用户与多数 crontab 文档习惯写 5 段。这里把 5 段补成 {@code 0 分 时 日 月 周}，
 * 两种写法都能用，存库统一成 6 段（与 {@code scan_task.cron_expression} 的注释一致）。</p>
 *
 * <p>Spring 的 6 段 cron 支持在"日 / 周"里写 {@code ?}（与 {@code *} 等价），
 * 所以 Quarz 风格的 {@code 0 0 2 * * ?} 也能直接使用。</p>
 */
public final class ScheduleCrons {

    /** {@code data_source.schedule_cron} 是 VARCHAR(64)。 */
    private static final int MAX_LENGTH = 64;

    private ScheduleCrons() {
    }

    /**
     * 归一并校验 cron。
     *
     * @param cron 用户输入，允许 5 段或 6 段
     * @return 6 段 cron；输入为空时返回 {@code null}
     * @throws BusinessException 表达式非法或过长（错误码 2，消息给出示例）
     */
    public static String normalize(String cron) {
        if (!StringUtils.hasText(cron)) {
            return null;
        }
        String value = cron.trim().replaceAll("\\s+", " ");
        if (value.length() > MAX_LENGTH) {
            throw invalid(cron, "表达式过长（上限 " + MAX_LENGTH + " 个字符）");
        }
        if (value.split(" ").length == 5) {
            value = "0 " + value;
        }
        try {
            CronExpression.parse(value);
        } catch (IllegalArgumentException e) {
            throw invalid(cron, e.getMessage());
        }
        return value;
    }

    /**
     * 计算下一次触发时间。
     *
     * @param normalizedCron {@link #normalize(String)} 的返回值
     * @param after          从这个时刻之后开始算
     * @return 下一次触发时间；表达式不会再触发时返回 {@code null}
     */
    public static LocalDateTime next(String normalizedCron, LocalDateTime after) {
        return CronExpression.parse(normalizedCron).next(after);
    }

    private static BusinessException invalid(String cron, String reason) {
        return new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                "cron 表达式不合法（" + reason + "）：" + cron
                        + "；示例：0 0 2 * * *（每天 02:00）、0 */30 * * * *（每 30 分钟）");
    }
}
