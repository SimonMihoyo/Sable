package me.kirara.sable.discovery.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.dto.DataSourceQuery;
import me.kirara.sable.discovery.dto.DataSourceSaveRequest;
import me.kirara.sable.discovery.entity.DataSource;

import java.util.List;

/**
 * 数据源业务服务。
 *
 * <p>数据源是租户级资产（表在 {@code tenant_{id}.data_source} 下），因此这里所有方法
 * 都依赖 {@code TenantContext}：缺租户上下文时由 {@code TenantRequiredInterceptor}
 * 在 MyBatis 层直接报 40104，不会静默落到 public schema。</p>
 *
 * <p>口令的加解密在这一层完成，控制器拿到的是已加密的实体 / 脱敏的投影，
 * 任何接口都不返回 {@code password_enc}（见 {@code DataSourceResult}）。</p>
 */
public interface DataSourceService extends IService<DataSource> {

    /**
     * 新增数据源。
     *
     * @param request 请求体；{@code password} 必填
     * @return 落库后的实体（含自动填充的 id 与时间戳）
     * @throws me.kirara.sable.common.BusinessException 名称重复（40203）或口令为空（2）
     */
    DataSource create(DataSourceSaveRequest request);

    /**
     * 更新数据源。
     *
     * <p>语义是"提供什么改什么"：请求里为 {@code null} 的字段不参与更新
     * （MyBatis-Plus 默认的 NOT_NULL 策略），因此 {@code password} 留空即"不改口令"。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）或名称重复（40203）
     */
    void update(Long id, DataSourceSaveRequest request);

    /**
     * 删除数据源（软删）并级联清理资产元数据。
     *
     * <p>为什么不是物理删：{@code data_source} 有 {@code deleted_at}，索引也都是
     * {@code WHERE deleted_at IS NULL} 的部分索引，设计意图就是软删；直接
     * {@code removeById} 还会撞上 {@code db_schema} / {@code scan_task} 的外键。</p>
     *
     * <p>级联范围按 API 文档「级联删除关联的 Schema、Table、Column」：这三张表
     * 没有 {@code deleted_at}，且内容可由重新扫描重建，因此物理删除（叶子 → 根）。
     * {@code scan_task} 保留 —— 它是执行历史，且外键仍然满足（父行还在），删掉反而丢审计线索。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）
     */
    void deleteCascade(Long id);

    /**
     * 按 ID 取数据源，不存在或已删则报 40201。
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）
     */
    DataSource requireById(Long id);

    /** 分页查询（自动排除已软删的数据源，关键字匹配名称与描述）。 */
    PageResult<DataSource> page(DataSourceQuery query);

    /** 当前租户下出现过的分组名（去重、按分组顺序排序，排除已软删的数据源）。 */
    List<String> groups();

    /**
     * 调整分组顺序。
     *
     * <p>请求里的分组按数组顺序排在前面，未出现在数组里的分组保持原有相对顺序、排在后面。
     * 整组会重新编号成 0..n-1（不做局部覆盖），避免出现并列导致顺序不可预期。
     * 不认识的分组名直接忽略 —— 前端提交时可能刚删掉一个组，不该因此整单失败。</p>
     *
     * @param groups 分组名，按期望的显示顺序排列
     */
    void reorderGroups(List<String> groups);

    /**
     * 测试连通性 —— 用库里存的口令解密后真正连一次目标库。
     *
     * <p>成功 / 失败都作为"探测结果"返回（接口仍是 {@code code=0}），数据源不存在才是
     * 错误（40201）。探测成功把状态写成 {@code ACTIVE}、失败写成 {@code ERROR}；
     * 若该类型没有可用驱动（项目只内置 PostgreSQL 驱动），只返回原因，**不改状态**
     * —— 环境缺驱动不该把租户的数据源标成异常。</p>
     *
     * @throws me.kirara.sable.common.BusinessException 数据源不存在（40201）
     */
    ConnectionTestResult testConnection(Long id);
}
