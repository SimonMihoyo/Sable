/**
 * 数据源连通性探测。
 *
 * <p>只做"能不能连上"这一件事：按 {@code data_source} 的类型拼 JDBC URL、带上解密后的口令
 * 直连一次，返回成功 / 失败原因 / 耗时 / 服务端版本。不建连接池、不改数据（状态回写由
 * {@code DataSourceService#testConnection} 负责）。</p>
 *
 * <p>依赖约束：项目只内置 PostgreSQL 驱动，因此 MySQL / Oracle / SQL Server 与
 * MongoDB / Redis 的探测会以"不可探测 + 原因"返回，而不是伪造成功。</p>
 */
package me.kirara.sable.discovery.connect;
