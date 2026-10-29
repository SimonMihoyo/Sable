package me.kirara.sable.discovery.engine;

import me.kirara.sable.discovery.entity.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

/**
 * 元数据扫描引擎 —— 从一个已建立的 JDBC 连接里读出 schema / table / column。
 *
 * <p>引擎不负责建连接、不负责落库：连接的生命周期与口令解密在服务层，
 * 落库由 {@code AssetSyncService} 完成。这样换数据库方言只需要换实现，
 * 扫描任务的状态机与资产同步逻辑不受影响。</p>
 */
public interface MetadataScanEngine {

    /**
     * 读取整库元数据。
     *
     * @param source    数据源配置（用于日志与方言判断，连接已由调用方建好）
     * @param connection 目标库连接，调用方负责关闭
     * @param cancelled  取消信号：每读完一个 schema 检查一次，返回 true 时抛
     *                   {@link ScanCancelledException}
     * @return 不可变快照，顺序确定（schema / table 按名称，column 按序号）
     * @throws SQLException           读取元数据失败
     * @throws ScanCancelledException 被取消
     */
    MetadataSnapshot scan(DataSource source, Connection connection, BooleanSupplier cancelled) throws SQLException;
}
