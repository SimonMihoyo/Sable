/**
 * 数据库方言 —— 各库在"schema 是什么、URL 怎么拼、注释怎么读、类型名怎么显示"上的差异。
 *
 * <p>扫描引擎只写一套标准 JDBC 元数据流程，库之间的不同都收敛到这里的
 * {@link me.kirara.sable.discovery.connect.dialect.JdbcDialect} 实现：
 * 当前支持 PostgreSQL 与 MySQL；Oracle / SQL Server 的驱动与方言尚未引入。</p>
 */
package me.kirara.sable.discovery.connect.dialect;
