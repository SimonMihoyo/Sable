package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.connect.JdbcConnectionTester;
import me.kirara.sable.discovery.crypto.DataSourceCipher;
import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.dto.DataSourceQuery;
import me.kirara.sable.discovery.dto.DataSourceSaveRequest;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.entity.DbColumn;
import me.kirara.sable.discovery.entity.DbSchema;
import me.kirara.sable.discovery.entity.DbTable;
import me.kirara.sable.discovery.mapper.DataSourceMapper;
import me.kirara.sable.discovery.service.DataSourceService;
import me.kirara.sable.discovery.service.DbColumnService;
import me.kirara.sable.discovery.service.DbSchemaService;
import me.kirara.sable.discovery.service.DbTableService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DataSource 业务服务实现。
 *
 * <p>实现里有两处刻意的写法，改动前请先读：</p>
 *
 * <ol>
 *   <li><b>所有查询都显式带 {@code deleted_at IS NULL}</b>：项目没有启用
 *       MyBatis-Plus 的逻辑删除（{@code @TableLogic} 会引入全局配置，影响面不止这一张表），
 *       因此"已软删"必须由每条查询自己排除。漏一条的表现是"删了还在列表里" ——
 *       不会报错，只会让人觉得删除按钮坏了。</li>
 *   <li><b>名称查重既查库又接唯一索引</b>：先查一次是为了给用户一句"名称重复"，
 *       兜底的则是 V2 迁移里的部分唯一索引（{@code uq_ds_name_active}）——
 *       并发下两个请求可能都通过前置检查，只有数据库能真正拦住。</li>
 * </ol>
 */
@Service
public class DataSourceServiceImpl extends ServiceImpl<DataSourceMapper, DataSource> implements DataSourceService {

    private final DataSourceCipher cipher;

    private final DbSchemaService dbSchemaService;

    private final DbTableService dbTableService;

    private final DbColumnService dbColumnService;

    private final JdbcConnectionTester connectionTester;

    public DataSourceServiceImpl(DataSourceCipher cipher,
                                 JdbcConnectionTester connectionTester,
                                 DbSchemaService dbSchemaService,
                                 DbTableService dbTableService,
                                 DbColumnService dbColumnService) {
        this.cipher = cipher;
        this.connectionTester = connectionTester;
        this.dbSchemaService = dbSchemaService;
        this.dbTableService = dbTableService;
        this.dbColumnService = dbColumnService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataSource create(DataSourceSaveRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            // 库里 password_enc 是 NOT NULL，与其让数据库报约束冲突（500），不如在这里说清楚
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "新增数据源时口令不能为空");
        }
        String name = request.getName().trim();
        assertNameAvailable(name, null);

        DataSource source = new DataSource();
        applyRequest(source, request);
        source.setName(name);
        if (StringUtils.hasText(source.getGroupName())) {
            source.setGroupSort(resolveGroupSort(source.getGroupName()));
        }
        source.setPasswordEnc(cipher.encrypt(request.getPassword()));
        source.setStatus("INACTIVE");
        source.setCreatedBy(currentUserId());
        try {
            save(source);
        } catch (DuplicateKeyException e) {
            // 前置检查与写入之间被别的请求插了同名数据源
            throw new BusinessException(ErrorCode.DATASOURCE_DUPLICATE);
        }
        return source;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DataSourceSaveRequest request) {
        DataSource current = requireById(id);
        String name = request.getName().trim();
        assertNameAvailable(name, id);

        DataSource source = new DataSource();
        source.setId(id);
        applyRequest(source, request);
        source.setName(name);
        String targetGroup = source.getGroupName();
        if (StringUtils.hasText(targetGroup) && !targetGroup.equals(current.getGroupName())) {
            // 换了分组：新组排到最后，已有组则对齐该组的排序，否则这一行会带着默认值 0
            // 把目标分组整体拽到最前面
            source.setGroupSort(resolveGroupSort(targetGroup));
        }
        // 留空 = 不改口令；只有真的传了才重新加密
        if (StringUtils.hasText(request.getPassword())) {
            source.setPasswordEnc(cipher.encrypt(request.getPassword()));
        }
        try {
            updateById(source);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DATASOURCE_DUPLICATE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCascade(Long id) {
        requireById(id);

        List<Long> schemaIds = dbSchemaService.list(new LambdaQueryWrapper<DbSchema>()
                        .select(DbSchema::getId)
                        .eq(DbSchema::getSourceId, id))
                .stream()
                .map(DbSchema::getId)
                .toList();
        if (!schemaIds.isEmpty()) {
            List<Long> tableIds = dbTableService.list(new LambdaQueryWrapper<DbTable>()
                            .select(DbTable::getId)
                            .in(DbTable::getSchemaId, schemaIds))
                    .stream()
                    .map(DbTable::getId)
                    .toList();
            if (!tableIds.isEmpty()) {
                dbColumnService.remove(new LambdaQueryWrapper<DbColumn>().in(DbColumn::getTableId, tableIds));
            }
            dbTableService.remove(new LambdaQueryWrapper<DbTable>().in(DbTable::getSchemaId, schemaIds));
            dbSchemaService.remove(new LambdaQueryWrapper<DbSchema>().in(DbSchema::getId, schemaIds));
        }

        DataSource softDeleted = new DataSource();
        softDeleted.setId(id);
        softDeleted.setDeletedAt(LocalDateTime.now());
        updateById(softDeleted);
    }

    @Override
    public DataSource requireById(Long id) {
        DataSource source = getById(id);
        if (source == null || source.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.DATASOURCE_NOT_FOUND);
        }
        return source;
    }

    @Override
    public PageResult<DataSource> page(DataSourceQuery query) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(DataSource::getDeletedAt)
                .eq(StringUtils.hasText(query.getType()), DataSource::getType, query.getType())
                .eq(StringUtils.hasText(query.getStatus()), DataSource::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getGroupName()), DataSource::getGroupName, query.getGroupName());
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(DataSource::getName, keyword)
                    .or()
                    .like(DataSource::getDescription, keyword));
        }
        wrapper.orderByDesc(DataSource::getCreatedAt);
        Page<DataSource> page = page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    @Override
    public List<String> groups() {
        // 排序挂在数据源行上，同一分组的多行同值；用 MIN 聚合排序可以容忍个别行被手工改坏
        // （直接 ORDER BY group_sort 在分组查询里既非法、也会各说各话）。
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(DataSource::getGroupName)
                .isNull(DataSource::getDeletedAt)
                .isNotNull(DataSource::getGroupName)
                .groupBy(DataSource::getGroupName)
                // 聚合排序（MIN）没法用 SFunction 表达，只能落到 last —— 固定字符串，无用户输入
                .last("ORDER BY MIN(group_sort) ASC, group_name ASC");
        return listObjs(wrapper, Object::toString);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        // 重新编号成一个**总序**，而不是只改数组里的那几个：
        // 只改一部分会留下并列（例如把 C 提到最前、A 从 0 改成 1，就和保持原值的 B 撞上），
        // 并列时只剩"按名字"这一个兜底规则，用户看到的结果会莫名其妙。
        List<String> ordered = new ArrayList<>();
        List<String> existing = groups();
        for (String raw : groups) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = raw.trim();
            // 不认识的组名忽略：前端提交时可能刚删掉一个组
            if (existing.contains(name) && !ordered.contains(name)) {
                ordered.add(name);
            }
        }
        // 未出现在请求里的分组保持在原有相对顺序中，排在后面
        for (String name : existing) {
            if (!ordered.contains(name)) {
                ordered.add(name);
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            update(new LambdaUpdateWrapper<DataSource>()
                    .set(DataSource::getGroupSort, i)
                    .eq(DataSource::getGroupName, ordered.get(i))
                    .isNull(DataSource::getDeletedAt));
        }
    }

    @Override
    public ConnectionTestResult testConnection(Long id) {
        DataSource source = requireById(id);
        if (!connectionTester.isTestable(source)) {
            // 没有驱动 / 不是 JDBC 类型：只回报原因，不把数据源状态改成 ERROR
            return ConnectionTestResult.failure(connectionTester.untestableReason(source), 0L);
        }
        String password;
        try {
            password = cipher.decrypt(source.getPasswordEnc());
        } catch (IllegalStateException e) {
            // 密钥换过 —— 存量密文解不开。这属于部署问题，给一句能指导动作的话
            return ConnectionTestResult.failure("存量口令无法解密（加密密钥可能已更换），请重新录入口令", 0L);
        }
        ConnectionTestResult result = connectionTester.test(source, password);
        DataSource statusUpdate = new DataSource();
        statusUpdate.setId(id);
        statusUpdate.setStatus(result.isSuccess() ? "ACTIVE" : "ERROR");
        updateById(statusUpdate);
        return result;
    }

    /**
     * 取某个分组应使用的排序值。
     *
     * <p>分组已存在 → 对齐它当前最小的排序值（把数据源挪进一个已有分组时，
     * 这一行不能带着默认的 0 把整组拽到最前）；分组不存在 → 排在所有分组之后。
     * 排序是"同一分组所有行同值"的反范式设计，这里就是维护该不变量的地方。</p>
     */
    private int resolveGroupSort(String groupName) {
        Page<DataSource> page = page(new Page<>(1, 1), new LambdaQueryWrapper<DataSource>()
                .select(DataSource::getGroupSort)
                .eq(DataSource::getGroupName, groupName)
                .isNull(DataSource::getDeletedAt)
                .orderByAsc(DataSource::getGroupSort));
        if (page.getRecords().isEmpty()) {
            return nextGroupSort();
        }
        Integer sort = page.getRecords().get(0).getGroupSort();
        return sort == null ? 0 : sort;
    }

    /** 新分组的排序值：现有最大值 + 1（没有分组时从 0 开始），保证新组排在末尾。 */
    private int nextGroupSort() {
        Page<DataSource> page = page(new Page<>(1, 1), new LambdaQueryWrapper<DataSource>()
                .isNull(DataSource::getDeletedAt)
                .orderByDesc(DataSource::getGroupSort));
        if (page.getRecords().isEmpty()) {
            return 0;
        }
        Integer max = page.getRecords().get(0).getGroupSort();
        return (max == null ? 0 : max) + 1;
    }

    /** 把请求里除口令、创建人、状态之外的字段搬到实体上。 */
    private static void applyRequest(DataSource source, DataSourceSaveRequest request) {
        source.setType(request.getType());
        source.setHost(request.getHost());
        source.setPort(request.getPort());
        source.setDatabaseName(request.getDatabaseName());
        source.setUsername(request.getUsername());
        source.setJdbcOptions(request.getJdbcOptions());
        source.setGroupName(StringUtils.hasText(request.getGroupName()) ? request.getGroupName().trim() : null);
        source.setDescription(request.getDescription());
    }

    /**
     * 名称在租户内唯一（软删的行不占名）。
     *
     * <p>这只是一次友好提示用的前置检查，真正的约束是 V2 迁移里的部分唯一索引。</p>
     */
    private void assertNameAvailable(String name, Long excludeId) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<DataSource>()
                .eq(DataSource::getName, name)
                .isNull(DataSource::getDeletedAt)
                .ne(excludeId != null, DataSource::getId, excludeId);
        if (count(wrapper) > 0) {
            throw new BusinessException(ErrorCode.DATASOURCE_DUPLICATE);
        }
    }

    /**
     * 创建人。
     *
     * <p>取 {@link UserContext} 而不是从安全上下文挖 principal：令牌过滤器把用户名放进
     * principal 是给人看的，用户 ID 只落在 {@code UserContext}（见 {@code JwtAuthenticationFilter}）。</p>
     */
    private static Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 能走到这里说明接口没经过认证过滤器 —— 属于装配错误，不是用户输入问题
            throw new IllegalStateException("缺少登录用户上下文，无法记录数据源创建人");
        }
        return userId;
    }
}
