package me.kirara.sable.discovery.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.discovery.dto.ScanCreateRequest;
import me.kirara.sable.discovery.entity.ScanTask;

/**
 * 扫描任务业务服务。
 *
 * <p>任务在请求线程里创建并落库（{@code PENDING}），随后交给 {@code scanTaskExecutor}
 * 异步执行：连接目标库 → 读元数据 → 写资产表 → 回写任务与数据源状态。
 * 因为执行线程不是请求线程，<b>租户上下文必须显式传递</b>（见实现里的 {@code runScan}）——
 * 否则 MyBatis 会因缺少租户上下文直接报 40104。</p>
 */
public interface ScanTaskService extends IService<ScanTask> {

    /**
     * 创建并异步提交一次扫描。
     *
     * <p>同一数据源同一时刻只允许一个未结束（PENDING / RUNNING）的任务 ——
     * 两个扫描同时写同一批资产行会互相覆盖，且没有意义。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）/
     *                                                  已有未结束任务、类型非法（2）
     */
    ScanTask submit(Long sourceId, ScanCreateRequest request);

    /**
     * 取消扫描任务。
     *
     * <p>只对 PENDING / RUNNING 生效；CANCELLED 视为幂等成功。
     * 执行线程在每个 schema / 表之间检查状态，尽快停下且**不覆盖** CANCELLED。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 任务不存在或已结束（2）
     */
    void cancel(Long taskId);
}
