package me.kirara.sable.discovery.connect;

import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.entity.DataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据源连通性探测 —— 用库里存的口令真正连一次目标库。
 *
 * <p>连接怎么建（URL、超时、驱动可用性）都在 {@link JdbcConnectionFactory}；
 * 这里只负责"连一次、量耗时、把异常翻译成人话"。</p>
 *
 * <p><b>为什么不用连接池：</b>这是一次性探测，成功与否只看能不能连上；建池反而引入
 * "探测完还得记得关池"的生命周期问题。用 {@link java.sql.DriverManager} 直连、
 * try-with-resources 关闭。</p>
 */
@Component
public class JdbcConnectionTester {

    private final JdbcConnectionFactory connectionFactory;

    public JdbcConnectionTester(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /** 是否值得发起探测：JDBC 类型且对应驱动在 classpath 上。 */
    public boolean isTestable(DataSource source) {
        return connectionFactory.isTestable(source);
    }

    /** 不可探测的原因（给用户看的一句话）。 */
    public String untestableReason(DataSource source) {
        return connectionFactory.untestableReason(source);
    }

    /**
     * 发起一次探测。
     *
     * @param source   数据源配置（host / port / databaseName / jdbcOptions / type）
     * @param password 解密后的明文口令，只用于本次连接，不落日志、不进返回值
     */
    public ConnectionTestResult test(DataSource source, String password) {
        long startedAt = System.nanoTime();
        try (Connection connection = connectionFactory.open(source, password)) {
            return ConnectionTestResult.success(connectionFactory.describeServer(connection), elapsedMs(startedAt));
        } catch (SQLException e) {
            return ConnectionTestResult.failure(humanMessage(e), elapsedMs(startedAt));
        }
    }

    /**
     * 把驱动异常翻译成一句可读的原因。
     *
     * <p>只保留驱动的简短消息，不拼接连接串 —— 连接串里有主机与库名，
     * 而 {@code jdbcOptions} 里用户可能塞了敏感参数。</p>
     */
    private static String humanMessage(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().trim();
        if (message.contains("No suitable driver")) {
            return "未找到对应的 JDBC 驱动";
        }
        if (message.isEmpty()) {
            return "连接失败（SQLState=" + e.getSQLState() + "）";
        }
        return "连接失败: " + message;
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
