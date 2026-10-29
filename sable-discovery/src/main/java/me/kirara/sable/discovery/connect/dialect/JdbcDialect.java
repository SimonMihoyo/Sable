package me.kirara.sable.discovery.connect.dialect;

import me.kirara.sable.discovery.entity.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * 数据库方言 —— "同一件事在不同库里怎么做"的唯一收口处。
 *
 * <p>扫描本身用的是标准 {@link java.sql.DatabaseMetaData}，但各库对"schema"的理解并不一样：
 * PostgreSQL 有 schema 层（{@code public}、{@code tenant_1}），MySQL 没有 schema、库就是
 * catalog，SQL Server 的 schema 是 {@code dbo} 这类。把这些差异放进方言，扫描引擎就可以
 * 保持"一套 JDBC 元数据流程"，新增数据库只需要多一个实现类，不用在引擎里加分支。</p>
 *
 * <p>对应需求文档 NFR-20「新数据源类型通过插件方式接入，无需修改核心代码」——
 * 现状是"一个 JDBC 引擎 + 每个库一个方言"，不是完整插件化，但至少新增同类关系库时
 * 不需要碰扫描逻辑。</p>
 */
public interface JdbcDialect {

    /** 对应的 {@code data_source.type} 取值，例如 {@code POSTGRESQL} / {@code MYSQL}。 */
    String type();

    /** 按数据源配置拼 JDBC URL。 */
    String buildUrl(DataSource source);

    /** 连接属性：用户名、口令、驱动级超时，以及该库特有的必需参数。 */
    Properties connectionProperties(DataSource source, String password);

    /**
     * 要扫描的 schema 列表。
     *
     * <p>MySQL 没有 schema，实现返回配置里的 {@code databaseName} —— 一个数据源只扫一个库，
     * 与 PostgreSQL"一个数据源一个库"的心智一致；要扫同实例的其他库就再建一个数据源
     * （这也符合最小权限：账号通常只被授权到业务库）。</p>
     */
    List<String> schemas(Connection connection, DataSource source) throws SQLException;

    /** 调 {@code getTables/getColumns/getPrimaryKeys} 时传的 catalog。 */
    String catalog(DataSource source, String schema);

    /** 调 {@code getTables/getColumns/getPrimaryKeys} 时传的 schemaPattern。 */
    String schemaPattern(DataSource source, String schema);

    /**
     * 展示用类型名归一。
     *
     * <p>不同驱动给的名字形态不同（PostgreSQL 报 {@code int4}，MySQL 报 {@code INT}），
     * 归一是为了让资产清单里同一类字段看起来一致，不改变字段本身的语义。</p>
     */
    default String normalizeTypeName(String rawType) {
        return rawType;
    }
}
