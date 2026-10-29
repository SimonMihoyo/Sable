-- MySQL 元数据扫描集成测试的建库脚本（Testcontainers 启动后执行）。
-- 目的：覆盖与 PostgreSQL 不同的三处 —— 库/schema 语义、表/字段注释、类型名形态。
CREATE TABLE customer (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(20)  NOT NULL COMMENT '手机号',
    full_name  VARCHAR(64)  COMMENT '姓名',
    created_at DATETIME     COMMENT '创建时间'
) COMMENT = '客户主表';

-- 同实例的另一个库：扫描器必须**忽略**它（一个数据源只扫配置的库）
CREATE DATABASE other_db;
CREATE TABLE other_db.secret_table (
    id     INT          PRIMARY KEY,
    secret VARCHAR(32)  COMMENT '不该被扫到的字段'
);
