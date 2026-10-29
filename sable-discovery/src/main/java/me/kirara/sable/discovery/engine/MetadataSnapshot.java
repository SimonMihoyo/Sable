package me.kirara.sable.discovery.engine;

import java.util.List;

/**
 * 一次元数据扫描的内存快照 —— 引擎产出、同步服务消费。
 *
 * <p>刻意做成不可变的记录对象：引擎只负责"从数据库读出结构"，不碰持久化；
 * 落库、去重、删除陈旧对象都由 {@code AssetSyncService} 处理。这样引擎可以在
 * 没有库的情况下对着一个 {@code Connection} 单独验证，同步逻辑也能独立测。</p>
 *
 * <p>字段与 {@code db_schema} / {@code db_table} / {@code db_column} 一一对应，
 * 但**不**包含分类分级字段（sensitivity / category / …）—— 那些是人工或规则产出的，
 * 重新扫描不得覆盖。</p>
 */
public final class MetadataSnapshot {

    private final List<Schema> schemas;

    public MetadataSnapshot(List<Schema> schemas) {
        this.schemas = List.copyOf(schemas);
    }

    public List<Schema> schemas() {
        return schemas;
    }

    public int schemaCount() {
        return schemas.size();
    }

    public int tableCount() {
        return schemas.stream().mapToInt(schema -> schema.tables().size()).sum();
    }

    public int columnCount() {
        return schemas.stream()
                .flatMap(schema -> schema.tables().stream())
                .mapToInt(table -> table.columns().size())
                .sum();
    }

    /** 一个数据库 schema。 */
    public record Schema(String name, String comment, List<Table> tables) {
        public Schema(String name, String comment, List<Table> tables) {
            this.name = name;
            this.comment = comment;
            this.tables = List.copyOf(tables);
        }
    }

    /** 一张表或视图。 */
    public record Table(String name, String type, String comment, List<Column> columns) {
        public Table(String name, String type, String comment, List<Column> columns) {
            this.name = name;
            this.type = type;
            this.comment = comment;
            this.columns = List.copyOf(columns);
        }
    }

    /**
     * 一个字段。
     *
     * @param charMaxLength  JDBC 的 COLUMN_SIZE（字符型长度；数值型时语义不同，见引擎注释）
     * @param numericPrecision 数值精度，当前未从 JDBC 元数据中解析（保留字段，见 TODO）
     */
    public record Column(String name,
                         Integer ordinal,
                         String dataType,
                         Integer charMaxLength,
                         Integer numericPrecision,
                         String comment,
                         boolean primaryKey,
                         boolean nullable,
                         String defaultValue) {
    }
}
