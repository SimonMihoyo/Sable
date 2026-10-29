package me.kirara.sable.discovery.schedule;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link ScheduleCrons} 的归一、校验与下次触发时间计算。 */
class ScheduleCronsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);

    @Test
    @DisplayName("5 段 cron 自动补秒位，6 段原样保留")
    void normalizesFiveFieldCron() {
        assertEquals("0 0 2 * * *", ScheduleCrons.normalize("0 2 * * *"));
        assertEquals("0 0 2 * * *", ScheduleCrons.normalize("  0   0  2 * * * "));
        assertEquals("*/30 * * * * *", ScheduleCrons.normalize("*/30 * * * * *"));
    }

    @Test
    @DisplayName("Quartz 风格的 ? 可用（Spring 6 等价于 *）")
    void acceptsQuestionMark() {
        assertEquals("0 0 2 * * ?", ScheduleCrons.normalize("0 0 2 * * ?"));
        assertEquals(LocalDateTime.of(2026, 10, 30, 2, 0), ScheduleCrons.next("0 0 2 * * ?", NOW));
    }

    @Test
    @DisplayName("空值视为未配置")
    void blankReturnsNull() {
        assertNull(ScheduleCrons.normalize(null));
        assertNull(ScheduleCrons.normalize("   "));
    }

    @Test
    @DisplayName("非法表达式给出可读错误码与示例")
    void rejectsInvalidCron() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ScheduleCrons.normalize("every day at 2"));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        assertEquals(true, ex.getMessage().contains("0 0 2 * * *"));
    }

    @Test
    @DisplayName("下次触发时间：从当前时刻之后算，不回放过去的槽位")
    void computesNextRun() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 2, 0),
                ScheduleCrons.next("0 0 2 * * *", LocalDateTime.of(2026, 10, 29, 3, 0)));
        assertEquals(LocalDateTime.of(2026, 10, 29, 10, 15),
                ScheduleCrons.next("0 */15 * * * *", NOW));
    }
}
