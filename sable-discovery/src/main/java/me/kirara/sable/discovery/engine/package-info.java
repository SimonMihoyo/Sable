/**
 * 元数据扫描引擎 —— 从 JDBC 连接读取 schema / table / column 结构。
 *
 * <p>引擎只产出不可变快照 {@link me.kirara.sable.discovery.engine.MetadataSnapshot}，
 * 不建连接、不落库：连接与口令解密在服务层，落库在
 * {@code me.kirara.sable.discovery.service.AssetSyncService}。取消通过
 * {@link me.kirara.sable.discovery.engine.ScanCancelledException} 表达。</p>
 */
package me.kirara.sable.discovery.engine;
