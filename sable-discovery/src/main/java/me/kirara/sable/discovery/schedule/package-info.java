/**
 * 定时扫描的调度支持 —— 目前只有 cron 归一 / 校验 / 取下一次触发时间。
 *
 * <p>触发本身由启动模块的 {@code ScanScheduleJob} 驱动（每 {@code sable.discovery.schedule.poll-interval}
 * 毫秒扫一次"到期"的数据源），这里保持纯计算，避免把 Spring 调度器混进业务逻辑。</p>
 */
package me.kirara.sable.discovery.schedule;
