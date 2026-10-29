package me.kirara.sable.discovery.engine;

import me.kirara.sable.discovery.connect.dialect.JdbcDialect;
import me.kirara.sable.discovery.connect.dialect.JdbcDialectRegistry;
import me.kirara.sable.discovery.entity.DataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 基于 JDBC {@link DatabaseMetaData} 的元数据扫描实现。
 *
 * <p><b>只用标准元数据 API，不写方言 SQL：</b>换数据库时不需要维护第二套查询。
 * 代价是每张表要多两次往返（{@code getColumns} / {@code getPrimaryKeys}），
 * 大库首次扫描会慢一些 —— 这是刻意的取舍，等真的出现性能问题再按方言批量化
 * （见下方 TODO）。</p>
 *
 * <p><b>schema / catalog 的取法交给方言：</b>PostgreSQL 枚举 {@code getSchemas()} 并过滤
 * 系统 schema；MySQL 没有 schema，方言直接返回配置里的库名并把它当 catalog。
 * 引擎本身不判断数据库种类，只按方言给出的参数调元数据。</p>
 */
@Component
public class MetadataScanEngineImpl implements MetadataScanEngine {

    private static final String[] TABLE_TYPES = {"TABLE", "VIEW"};

    private final JdbcDialectRegistry dialects;

    public MetadataScanEngineImpl(JdbcDialectRegistry dialects) {
        this.dialects = dialects;
    }

    @Override
    public MetadataSnapshot scan(DataSource source, Connection connection, BooleanSupplier cancelled)
            throws SQLException {
        JdbcDialect dialect = dialects.resolve(source.getType());
        if (dialect == null) {
            throw new IllegalStateException("暂不支持的数据源类型: " + source.getType());
        }
        DatabaseMetaData metaData = connection.getMetaData();
        List<MetadataSnapshot.Schema> schemas = new ArrayList<>();
        for (String schemaName : dialect.schemas(connection, source)) {
            if (cancelled.getAsBoolean()) {
                throw new ScanCancelledException();
            }
            schemas.add(new MetadataSnapshot.Schema(schemaName, null,
                    readTables(metaData, dialect, source, schemaName, cancelled)));
        }
        schemas.sort(Comparator.comparing(MetadataSnapshot.Schema::name));
        return new MetadataSnapshot(schemas);
    }

    private List<MetadataSnapshot.Table> readTables(DatabaseMetaData metaData, JdbcDialect dialect,
                                                    DataSource source, String schemaName,
                                                    BooleanSupplier cancelled) throws SQLException {
        List<MetadataSnapshot.Table> tables = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(
                dialect.catalog(source, schemaName), dialect.schemaPattern(source, schemaName), "%", TABLE_TYPES)) {
            while (rs.next()) {
                if (cancelled.getAsBoolean()) {
                    throw new ScanCancelledException();
                }
                String tableName = rs.getString("TABLE_NAME");
                tables.add(new MetadataSnapshot.Table(
                        tableName,
                        rs.getString("TABLE_TYPE"),
                        rs.getString("REMARKS"),
                        readColumns(metaData, dialect, source, schemaName, tableName)));
            }
        }
        tables.sort(Comparator.comparing(MetadataSnapshot.Table::name));
        return tables;
    }

    private List<MetadataSnapshot.Column> readColumns(DatabaseMetaData metaData, JdbcDialect dialect,
                                                      DataSource source, String schemaName, String tableName)
            throws SQLException {
        String catalog = dialect.catalog(source, schemaName);
        String schemaPattern = dialect.schemaPattern(source, schemaName);

        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        List<MetadataSnapshot.Column> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                columns.add(new MetadataSnapshot.Column(
                        columnName,
                        integerOrNull(rs, "ORDINAL_POSITION"),
                        dialect.normalizeTypeName(rs.getString("TYPE_NAME")),
                        integerOrNull(rs, "COLUMN_SIZE"),
                        // TODO 数值列的精度在 JDBC 里同样是 COLUMN_SIZE，当前与字符长度共用一列；
                        //      细分需要在类型层面判断，等分类分级需要精确精度时再做。
                        null,
                        rs.getString("REMARKS"),
                        primaryKeys.contains(columnName),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        rs.getString("COLUMN_DEF")));
            }
        }
        columns.sort(Comparator.comparing(column -> column.ordinal() == null ? Integer.MAX_VALUE : column.ordinal()));
        return columns;
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
