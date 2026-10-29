package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.kirara.sable.discovery.engine.MetadataSnapshot;
import me.kirara.sable.discovery.entity.DbColumn;
import me.kirara.sable.discovery.entity.DbSchema;
import me.kirara.sable.discovery.entity.DbTable;
import me.kirara.sable.discovery.service.AssetSyncService;
import me.kirara.sable.discovery.service.DbColumnService;
import me.kirara.sable.discovery.service.DbSchemaService;
import me.kirara.sable.discovery.service.DbTableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产同步实现 —— 全量对齐 + 保留人工分级。
 *
 * <p>先把目标数据源现有的 schema / table / column 一次性读进内存，再与快照做差量：
 * 这样每张表只写一条语句，而不是"每行先 select 再 insert/update"。首次扫描大库时
 * 内存占用与对象数成正比，MVP 规模可以接受。</p>
 */
@Service
public class AssetSyncServiceImpl implements AssetSyncService {

    private static final int BATCH_SIZE = 500;

    private final DbSchemaService dbSchemaService;

    private final DbTableService dbTableService;

    private final DbColumnService dbColumnService;

    public AssetSyncServiceImpl(DbSchemaService dbSchemaService,
                                DbTableService dbTableService,
                                DbColumnService dbColumnService) {
        this.dbSchemaService = dbSchemaService;
        this.dbTableService = dbTableService;
        this.dbColumnService = dbColumnService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sync(Long sourceId, MetadataSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();

        List<DbSchema> oldSchemas = dbSchemaService.list(new LambdaQueryWrapper<DbSchema>()
                .eq(DbSchema::getSourceId, sourceId));
        Map<String, DbSchema> schemaByName = oldSchemas.stream()
                .collect(Collectors.toMap(DbSchema::getSchemaName, Function.identity()));

        List<Long> oldSchemaIds = oldSchemas.stream().map(DbSchema::getId).toList();
        List<DbTable> oldTables = oldSchemaIds.isEmpty() ? List.of()
                : dbTableService.list(new LambdaQueryWrapper<DbTable>().in(DbTable::getSchemaId, oldSchemaIds));
        Map<TableKey, DbTable> tableByKey = oldTables.stream()
                .collect(Collectors.toMap(table -> new TableKey(table.getSchemaId(), table.getTableName()),
                        Function.identity()));

        List<Long> oldTableIds = oldTables.stream().map(DbTable::getId).toList();
        List<DbColumn> oldColumns = oldTableIds.isEmpty() ? List.of()
                : dbColumnService.list(new LambdaQueryWrapper<DbColumn>().in(DbColumn::getTableId, oldTableIds));
        Map<ColumnKey, DbColumn> columnByKey = oldColumns.stream()
                .collect(Collectors.toMap(column -> new ColumnKey(column.getTableId(), column.getColumnName()),
                        Function.identity()));

        Set<Long> keptSchemaIds = new HashSet<>();
        Set<Long> keptTableIds = new HashSet<>();
        Set<Long> keptColumnIds = new HashSet<>();
        List<DbTable> updatedTables = new ArrayList<>();
        List<DbColumn> newColumns = new ArrayList<>();
        List<DbColumn> updatedColumns = new ArrayList<>();

        for (MetadataSnapshot.Schema schema : snapshot.schemas()) {
            DbSchema schemaEntity = schemaByName.get(schema.name());
            if (schemaEntity == null) {
                schemaEntity = new DbSchema();
                schemaEntity.setSourceId(sourceId);
                schemaEntity.setSchemaName(schema.name());
                schemaEntity.setTableCount(schema.tables().size());
                schemaEntity.setScannedAt(now);
                dbSchemaService.save(schemaEntity);
            } else {
                schemaEntity.setTableCount(schema.tables().size());
                schemaEntity.setScannedAt(now);
                dbSchemaService.updateById(schemaEntity);
            }
            keptSchemaIds.add(schemaEntity.getId());

            for (MetadataSnapshot.Table table : schema.tables()) {
                DbTable tableEntity = tableByKey.get(new TableKey(schemaEntity.getId(), table.name()));
                if (tableEntity == null) {
                    tableEntity = new DbTable();
                    tableEntity.setSchemaId(schemaEntity.getId());
                    tableEntity.setTableName(table.name());
                    tableEntity.setTableType(table.type());
                    tableEntity.setTableComment(table.comment());
                    tableEntity.setColumnCount(table.columns().size());
                    // 行数与体积是方言相关的昂贵查询（count(*) / pg_class），本版不采集
                    tableEntity.setRowCount(0L);
                    tableEntity.setSizeBytes(0L);
                    tableEntity.setScannedAt(now);
                    dbTableService.save(tableEntity);
                } else {
                    tableEntity.setTableType(table.type());
                    tableEntity.setTableComment(table.comment());
                    tableEntity.setColumnCount(table.columns().size());
                    tableEntity.setScannedAt(now);
                    updatedTables.add(tableEntity);
                }
                keptTableIds.add(tableEntity.getId());

                for (MetadataSnapshot.Column column : table.columns()) {
                    DbColumn existing = columnByKey.get(new ColumnKey(tableEntity.getId(), column.name()));
                    // 一律构造新对象：只带结构字段，分类分级字段不出现，更新语句就不会碰它们
                    DbColumn entity = new DbColumn();
                    applyStructure(entity, column, now);
                    if (existing == null) {
                        entity.setTableId(tableEntity.getId());
                        entity.setColumnName(column.name());
                        newColumns.add(entity);
                    } else {
                        entity.setId(existing.getId());
                        keptColumnIds.add(existing.getId());
                        updatedColumns.add(entity);
                    }
                }
            }
        }

        if (!newColumns.isEmpty()) {
            dbColumnService.saveBatch(newColumns, BATCH_SIZE);
        }
        if (!updatedColumns.isEmpty()) {
            dbColumnService.updateBatchById(updatedColumns, BATCH_SIZE);
        }
        if (!updatedTables.isEmpty()) {
            dbTableService.updateBatchById(updatedTables, BATCH_SIZE);
        }

        removeStaleColumns(oldColumns, keptColumnIds);
        removeStaleTables(oldTables, keptTableIds);
        removeStaleSchemas(oldSchemas, keptSchemaIds);
    }

    /** 结构字段 —— 扫描可以刷新，人工/规则产出的分级字段不在此列。 */
    private static void applyStructure(DbColumn entity, MetadataSnapshot.Column column, LocalDateTime now) {
        entity.setOrdinalPos(column.ordinal());
        entity.setDataType(column.dataType());
        entity.setCharMaxLength(column.charMaxLength());
        entity.setNumericPrecision(column.numericPrecision());
        entity.setColumnComment(column.comment());
        entity.setIsPrimaryKey(column.primaryKey());
        entity.setIsNullable(column.nullable());
        entity.setDefaultValue(column.defaultValue());
        entity.setScannedAt(now);
    }

    private void removeStaleColumns(List<DbColumn> oldColumns, Set<Long> keptColumnIds) {
        List<Long> stale = oldColumns.stream()
                .map(DbColumn::getId)
                .filter(id -> !keptColumnIds.contains(id))
                .toList();
        if (!stale.isEmpty()) {
            dbColumnService.removeByIds(stale);
        }
    }

    private void removeStaleTables(List<DbTable> oldTables, Set<Long> keptTableIds) {
        List<Long> stale = oldTables.stream()
                .map(DbTable::getId)
                .filter(id -> !keptTableIds.contains(id))
                .toList();
        if (!stale.isEmpty()) {
            dbTableService.removeByIds(stale);
        }
    }

    private void removeStaleSchemas(List<DbSchema> oldSchemas, Set<Long> keptSchemaIds) {
        List<Long> stale = oldSchemas.stream()
                .map(DbSchema::getId)
                .filter(id -> !keptSchemaIds.contains(id))
                .toList();
        if (!stale.isEmpty()) {
            dbSchemaService.removeByIds(stale);
        }
    }

    private record TableKey(Long schemaId, String tableName) {
    }

    private record ColumnKey(Long tableId, String columnName) {
    }
}
