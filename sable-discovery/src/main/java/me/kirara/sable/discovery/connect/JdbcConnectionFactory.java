package me.kirara.sable.discovery.connect;

import me.kirara.sable.discovery.connect.dialect.JdbcDialect;
import me.kirara.sable.discovery.connect.dialect.JdbcDialectRegistry;
import me.kirara.sable.discovery.entity.DataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 数据源连接工厂 —— 解析方言、拼 JDBC URL、建立连接。
 *
 * <p>探测（{@link JdbcConnectionTester}）与元数据扫描共用这一份逻辑：URL 规则、超时参数、
 * 驱动可用性只在一个地方判断，不会出现"测试能连上、扫描连不上"这种两套实现漂移。
 * 各库的差异在 {@link JdbcDialect} 里（见 {@code dialect} 包），本类只负责调度。</p>
 *
 * <p><b>为什么不建连接池：</b>一次扫描从头到尾只用一条连接（顺序读元数据），池解决的是
 * "并发复用"而不是"单连接复用"。建池反而引入新的生命周期问题：数据源改了密码要重建池、
 * 数据源被删要关池、租户删除时池没人关（本项目已有同类已知缺口）。因此这里用
 * {@link DriverManager} 直连，由调用方 try-with-resources 关闭。</p>
 */
@Component
public class JdbcConnectionFactory {

    private final JdbcDialectRegistry dialects;

    public JdbcConnectionFactory(JdbcDialectRegistry dialects) {
        this.dialects = dialects;
    }

    /**
     * 建立连接。调用方负责关闭（建议 try-with-resources）。
     *
     * @param source   数据源配置
     * @param password 解密后的明文口令，只用于本次连接，不落日志
     * @throws SQLException 类型不支持、驱动缺失或连接失败
     */
    public Connection open(DataSource source, String password) throws SQLException {
        JdbcDialect dialect = dialects.resolve(source.getType());
        if (dialect == null) {
            throw new SQLException("暂不支持的数据源类型: " + source.getType());
        }
        return DriverManager.getConnection(dialect.buildUrl(source), dialect.connectionProperties(source, password));
    }

    /** 是否值得发起连接：有方言且对应驱动在 classpath 上。 */
    public boolean isTestable(DataSource source) {
        JdbcDialect dialect = dialects.resolve(source.getType());
        if (dialect == null) {
            return false;
        }
        try {
            // 没有匹配驱动时这里直接抛 "No suitable driver"
            return DriverManager.getDriver(dialect.buildUrl(source)) != null;
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    /** 不可连接的原因（给用户看的一句话）。 */
    public String untestableReason(DataSource source) {
        if (!dialects.supports(source.getType())) {
            return "暂不支持 " + source.getType() + " 类型的连接（当前支持 " + dialects.supportedTypes() + "）";
        }
        return "当前未内置 " + source.getType() + " 的 JDBC 驱动，无法连接";
    }

    /** 按方言拼 JDBC URL —— 主要供测试与日志排查使用。 */
    public String buildUrl(DataSource source) {
        JdbcDialect dialect = dialects.resolve(source.getType());
        if (dialect == null) {
            throw new IllegalArgumentException("不是受支持的 JDBC 数据源类型: " + source.getType());
        }
        return dialect.buildUrl(source);
    }

    /** 服务端版本，例如 {@code PostgreSQL 16.15}；取不到就返回 null（不影响"连上了"这个结论）。 */
    public String describeServer(Connection connection) {
        try {
            return connection.getMetaData().getDatabaseProductName()
                    + " " + connection.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            return null;
        }
    }
}
