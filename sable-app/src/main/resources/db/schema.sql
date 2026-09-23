-- Sablé 数据合规管理系统 — 数据库初始化脚本
-- 自动抽取自 doc/数据合规系统-数据库设计文档.md
-- 目标数据库：PostgreSQL 14+

CREATE TABLE public.tenant (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    code            VARCHAR(32)     NOT NULL UNIQUE,          -- 租户编码，用于生成schema名
    plan_type       VARCHAR(16)     NOT NULL DEFAULT 'BASIC', -- BASIC / PROFESSIONAL / ENTERPRISE
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',-- ACTIVE / SUSPENDED / EXPIRED
    contact_name    VARCHAR(64),
    contact_email   VARCHAR(128),
    contact_phone   VARCHAR(32),
    config          JSONB           NOT NULL DEFAULT '{}',    -- 租户级配置（配额、特性开关）
    -- config 示例:
    -- {
    --   "max_data_sources": 10,
    --   "max_users": 20,
    --   "storage_quota_gb": 50,
    --   "audit_log_retention_days": 180,
    --   "features": {
    --     "anomaly_detection": false,
    --     "custom_rules": true,
    --     "api_access": true
    --   }
    -- }
    expired_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_tenant_plan   CHECK (plan_type IN ('BASIC', 'PROFESSIONAL', 'ENTERPRISE')),
    CONSTRAINT chk_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED'))
);

CREATE INDEX idx_tenant_status ON public.tenant (status) WHERE deleted_at IS NULL;

CREATE TABLE public.sys_user (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(64)     NOT NULL UNIQUE,
    password_hash   VARCHAR(256)    NOT NULL,                  -- BCrypt哈希
    email           VARCHAR(128),
    phone           VARCHAR(32),
    display_name    VARCHAR(64),
    avatar_url      VARCHAR(512),
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / LOCKED / DISABLED
    mfa_enabled     BOOLEAN         NOT NULL DEFAULT FALSE,
    mfa_secret      VARCHAR(128),                              -- TOTP密钥（加密存储）
    last_login_at   TIMESTAMP,
    last_login_ip   VARCHAR(64),
    login_fail_count INT            NOT NULL DEFAULT 0,
    locked_until    TIMESTAMP,
    password_changed_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_sys_user_email ON public.sys_user (email) WHERE deleted_at IS NULL;
CREATE INDEX idx_sys_user_phone ON public.sys_user (phone) WHERE deleted_at IS NULL;

CREATE TABLE public.role (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL REFERENCES public.tenant(id),
    name            VARCHAR(64)     NOT NULL,                   -- SUPER_ADMIN / COMPLIANCE_OFFICER / AUDITOR / DATA_STEWARD / VIEWER
    display_name    VARCHAR(64)     NOT NULL,
    description     VARCHAR(256),
    is_system       BOOLEAN         NOT NULL DEFAULT FALSE,     -- 系统预置角色不可删除
    permissions     JSONB           NOT NULL DEFAULT '[]',      -- 权限列表
    -- permissions 示例:
    -- [
    --   "data_source:read", "data_source:write",
    --   "audit_log:read", "audit_log:export",
    --   "assessment:read", "assessment:write",
    --   "report:read", "report:export",
    --   "system:manage"
    -- ]
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT uq_role_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_role_tenant ON public.role (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE public.tenant_user (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL REFERENCES public.tenant(id),
    user_id         BIGINT          NOT NULL REFERENCES public.sys_user(id),
    role_id         BIGINT          NOT NULL REFERENCES public.role(id),
    department      VARCHAR(64),                                -- 数据范围权限维度
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT uq_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_tu_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_tenant_user_tenant ON public.tenant_user (tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenant_user_user   ON public.tenant_user (user_id)  WHERE deleted_at IS NULL;

CREATE TABLE public.sys_audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,                                     -- 可为NULL（系统级操作）
    user_id         BIGINT,
    user_name       VARCHAR(64),
    module          VARCHAR(32)     NOT NULL,                   -- TENANT / USER / ROLE / SYSTEM / CONFIG
    action          VARCHAR(32)     NOT NULL,                   -- CREATE / UPDATE / DELETE / LOGIN / LOGOUT / EXPORT
    target_type     VARCHAR(64),                                -- 操作对象类型
    target_id       BIGINT,                                     -- 操作对象ID
    detail          JSONB,                                      -- 变更详情（diff）
    source_ip       VARCHAR(64),
    user_agent      VARCHAR(256),
    result          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_sys_audit_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_sys_audit_tenant  ON public.sys_audit_log (tenant_id, created_at);
CREATE INDEX idx_sys_audit_user    ON public.sys_audit_log (user_id, created_at);
CREATE INDEX idx_sys_audit_time    ON public.sys_audit_log (created_at);

CREATE TABLE public.sys_config (
    id              BIGSERIAL       PRIMARY KEY,
    config_group    VARCHAR(64)     NOT NULL,                   -- 配置分组
    config_key      VARCHAR(128)    NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)     NOT NULL DEFAULT 'STRING',  -- STRING / NUMBER / BOOLEAN / JSON
    description     VARCHAR(256),
    is_encrypted    BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_config_group_key UNIQUE (config_group, config_key)
);

CREATE TABLE data_source (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    type            VARCHAR(32)     NOT NULL,                   -- MYSQL / POSTGRESQL / ORACLE / SQLSERVER / MONGODB / REDIS
    host            VARCHAR(256)    NOT NULL,
    port            INT             NOT NULL,
    database_name   VARCHAR(128)    NOT NULL,
    username        VARCHAR(128)    NOT NULL,
    password_enc    BYTEA           NOT NULL,                   -- pgcrypto加密
    jdbc_options    VARCHAR(512),                               -- 额外JDBC参数
    group_name      VARCHAR(64),
    description     VARCHAR(256),
    status          VARCHAR(16)     NOT NULL DEFAULT 'INACTIVE',-- ACTIVE / INACTIVE / ERROR / TESTING
    last_scan_at    TIMESTAMP,
    last_scan_status VARCHAR(16),                               -- SUCCESS / PARTIAL / FAILED
    scan_error_msg  TEXT,
    created_by      BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_ds_type   CHECK (type IN ('MYSQL', 'POSTGRESQL', 'ORACLE', 'SQLSERVER', 'MONGODB', 'REDIS')),
    CONSTRAINT chk_ds_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ERROR', 'TESTING')),
    CONSTRAINT chk_ds_port   CHECK (port BETWEEN 1 AND 65535)
);

CREATE INDEX idx_ds_group  ON data_source (group_name) WHERE deleted_at IS NULL;
CREATE INDEX idx_ds_status ON data_source (status) WHERE deleted_at IS NULL;

CREATE TABLE db_schema (
    id              BIGSERIAL       PRIMARY KEY,
    source_id       BIGINT          NOT NULL REFERENCES data_source(id),
    schema_name     VARCHAR(128)    NOT NULL,
    comment         VARCHAR(512),
    table_count     INT             DEFAULT 0,
    scanned_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_schema_source UNIQUE (source_id, schema_name)
);

CREATE INDEX idx_schema_source ON db_schema (source_id);

CREATE TABLE db_table (
    id              BIGSERIAL       PRIMARY KEY,
    schema_id       BIGINT          NOT NULL REFERENCES db_schema(id),
    table_name      VARCHAR(128)    NOT NULL,
    table_comment   VARCHAR(512),
    table_type      VARCHAR(32)     NOT NULL DEFAULT 'TABLE',   -- TABLE / VIEW
    row_count       BIGINT          DEFAULT 0,
    size_bytes      BIGINT          DEFAULT 0,
    column_count    INT             DEFAULT 0,
    scanned_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_table_schema UNIQUE (schema_id, table_name)
);

CREATE INDEX idx_table_schema ON db_table (schema_id);

CREATE TABLE db_column (
    id              BIGSERIAL       PRIMARY KEY,
    table_id        BIGINT          NOT NULL REFERENCES db_table(id),
    column_name     VARCHAR(128)    NOT NULL,
    ordinal_pos     INT             NOT NULL,                   -- 字段序号
    data_type       VARCHAR(64)     NOT NULL,
    char_max_length INT,
    numeric_precision INT,
    column_comment  VARCHAR(512),
    is_primary_key  BOOLEAN         NOT NULL DEFAULT FALSE,
    is_nullable     BOOLEAN         NOT NULL DEFAULT TRUE,
    default_value   TEXT,
    -- 分类分级
    sensitivity     VARCHAR(32),                                -- SPI / PI / IMPORTANT / GENERAL / NULL(未分类)
    category        VARCHAR(64),                                -- 分类：个人基本信息/身份信息/生物特征/财务信息/...
    matched_rule_id BIGINT,                                     -- 匹配的识别规则ID
    manual_override BOOLEAN         NOT NULL DEFAULT FALSE,     -- 是否人工修正
    -- 保护策略
    encryption_req  VARCHAR(16)     DEFAULT 'NONE',             -- NONE / AT_REST / IN_TRANSIT / BOTH
    masking_rule    VARCHAR(64),                                -- 脱敏规则名称
    scanned_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_column_table UNIQUE (table_id, column_name),
    CONSTRAINT chk_col_sensitivity CHECK (sensitivity IS NULL OR sensitivity IN ('SPI', 'PI', 'IMPORTANT', 'GENERAL'))
);

CREATE INDEX idx_column_table     ON db_column (table_id);
CREATE INDEX idx_column_sensitivity ON db_column (sensitivity) WHERE sensitivity IS NOT NULL;
CREATE INDEX idx_column_rule      ON db_column (matched_rule_id) WHERE matched_rule_id IS NOT NULL;

CREATE TABLE classify_rule (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(256),
    category        VARCHAR(64)     NOT NULL,                   -- 分类：PERSONAL_BASIC / IDENTITY / BIOLOGICAL / FINANCIAL / ...
    sensitivity     VARCHAR(32)     NOT NULL,                   -- 对应级别：SPI / PI / IMPORTANT / GENERAL
    match_type      VARCHAR(16)     NOT NULL,                   -- REGEX / KEYWORD / SAMPLE / COMPOSITE
    pattern         TEXT,                                       -- 正则表达式或关键词JSON
    -- pattern 示例 (REGEX): ^1[3-9]\d{9}$
    -- pattern 示例 (KEYWORD): ["手机号", "mobile", "phone"]
    -- pattern 示例 (COMPOSITE): {"regex": "...", "keywords": [...], "min_match": 2}
    priority        INT             NOT NULL DEFAULT 0,         -- 优先级，越大越优先
    is_builtin      BOOLEAN         NOT NULL DEFAULT FALSE,     -- 是否系统内置
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / DISABLED
    created_by      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_rule_match_type CHECK (match_type IN ('REGEX', 'KEYWORD', 'SAMPLE', 'COMPOSITE')),
    CONSTRAINT chk_rule_sensitivity CHECK (sensitivity IN ('SPI', 'PI', 'IMPORTANT', 'GENERAL'))
);

CREATE INDEX idx_rule_category ON classify_rule (category) WHERE deleted_at IS NULL;
CREATE INDEX idx_rule_status   ON classify_rule (status, priority DESC) WHERE deleted_at IS NULL;

-- 按月分区，支持高性能写入和历史数据管理
CREATE TABLE audit_log (
    id              BIGSERIAL,
    user_id         BIGINT          NOT NULL,
    user_name       VARCHAR(64)     NOT NULL,
    operation       VARCHAR(32)     NOT NULL,                   -- QUERY / UPDATE / DELETE / EXPORT / SHARE / LOGIN / CONFIG_CHANGE
    target_desc     VARCHAR(512)    NOT NULL,                   -- 目标数据描述：schema.table.column
    target_level    VARCHAR(32),                                -- 涉及数据的最高敏感级别
    source_ip       VARCHAR(64)     NOT NULL,
    request_path    VARCHAR(256),
    request_method  VARCHAR(16),
    operation_context TEXT,                                     -- 操作上下文（脱敏后的请求参数摘要）
    result          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS', -- SUCCESS / FAILURE / DENIED
    result_message  VARCHAR(256),
    duration_ms     INT,                                        -- 操作耗时
    -- 防篡改哈希链
    prev_hash       VARCHAR(64),                                -- 上一条日志的hash
    hash            VARCHAR(64)     NOT NULL,                   -- 本条日志的SHA-256哈希
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, created_at),

    CONSTRAINT chk_audit_operation CHECK (operation IN (
        'QUERY', 'UPDATE', 'DELETE', 'EXPORT', 'SHARE',
        'LOGIN', 'LOGOUT', 'CONFIG_CHANGE', 'DATA_ACCESS'
    )),
    CONSTRAINT chk_audit_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED'))
) PARTITION BY RANGE (created_at);

-- 自动创建月度分区（示例）
CREATE TABLE audit_log_2026_01 PARTITION OF audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE audit_log_2026_02 PARTITION OF audit_log
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE audit_log_2026_03 PARTITION OF audit_log
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
-- ... 后续分区由定时任务自动创建

-- 索引（每个分区自动继承）
CREATE INDEX idx_audit_user_time   ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_operation   ON audit_log (operation, created_at DESC);
CREATE INDEX idx_audit_target      ON audit_log (target_desc);
CREATE INDEX idx_audit_level       ON audit_log (target_level) WHERE target_level IS NOT NULL;
CREATE INDEX idx_audit_ip          ON audit_log (source_ip);
CREATE INDEX idx_audit_hash        ON audit_log (hash);

CREATE TABLE alert_rule (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(256),
    rule_type       VARCHAR(32)     NOT NULL,                   -- THRESHOLD / TIME_BASED / FREQUENCY / PATTERN
    config          JSONB           NOT NULL,
    -- THRESHOLD 示例:
    -- {"target_level": "SPI", "max_rows_per_query": 1000}
    -- TIME_BASED 示例:
    -- {"allowed_hours": [9,10,11,12,13,14,15,16,17], "days": ["MON","TUE","WED","THU","FRI"]}
    -- FREQUENCY 示例:
    -- {"max_operations": 100, "window_minutes": 10, "target_level": "SPI"}
    severity        VARCHAR(16)     NOT NULL DEFAULT 'WARNING', -- INFO / WARNING / CRITICAL
    notify_channels JSONB           NOT NULL DEFAULT '["SYSTEM"]', -- ["SYSTEM","EMAIL","WECHAT","DINGTALK"]
    notify_targets  JSONB,                                        -- ["admin@example.com", "webhook_url"]
    is_enabled      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,

    CONSTRAINT chk_alert_type     CHECK (rule_type IN ('THRESHOLD', 'TIME_BASED', 'FREQUENCY', 'PATTERN')),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE TABLE alert_event (
    id              BIGSERIAL       PRIMARY KEY,
    rule_id         BIGINT          NOT NULL REFERENCES alert_rule(id),
    rule_name       VARCHAR(128)    NOT NULL,
    severity        VARCHAR(16)     NOT NULL,
    trigger_user_id BIGINT,
    trigger_user    VARCHAR(64),
    trigger_detail  JSONB           NOT NULL,                   -- 触发详情（命中了什么条件）
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING / ACKNOWLEDGED / INVESTIGATING / RESOLVED / CLOSED
    handler_id      BIGINT,                                     -- 处理人
    handle_remark   TEXT,
    handled_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_alert_event_status CHECK (status IN ('PENDING', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_alert_event_status ON alert_event (status, created_at DESC);
CREATE INDEX idx_alert_event_rule   ON alert_event (rule_id, created_at DESC);
CREATE INDEX idx_alert_event_time   ON alert_event (created_at DESC);

CREATE TABLE assessment (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    standard_type   VARCHAR(32)     NOT NULL,                   -- PIPL / DSL / CUSTOM
    standard_version VARCHAR(32)    NOT NULL,                   -- 标准版本号
    description     VARCHAR(512),
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',   -- DRAFT / IN_PROGRESS / COMPLETED / ARCHIVED
    total_items     INT             NOT NULL DEFAULT 0,
    passed_items    INT             NOT NULL DEFAULT 0,
    partial_items   INT             NOT NULL DEFAULT 0,
    failed_items    INT             NOT NULL DEFAULT 0,
    na_items        INT             NOT NULL DEFAULT 0,         -- 不适用项
    score           DECIMAL(5,2),                               -- 百分制得分
    grade           VARCHAR(4),                                 -- A / B / C / D
    assessor_id     BIGINT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_by      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_assess_standard CHECK (standard_type IN ('PIPL', 'DSL', 'CUSTOM')),
    CONSTRAINT chk_assess_status   CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT chk_assess_grade    CHECK (grade IS NULL OR grade IN ('A', 'B', 'C', 'D'))
);

CREATE INDEX idx_assess_status ON assessment (status, created_at DESC);

CREATE TABLE assessment_item (
    id              BIGSERIAL       PRIMARY KEY,
    assessment_id   BIGINT          NOT NULL REFERENCES assessment(id),
    category        VARCHAR(64)     NOT NULL,                   -- 检查类别：组织管理 / 技术措施 / 操作流程 / 权利保障
    ref_law         VARCHAR(64),                                -- 对应法规：PIPL / DSL
    ref_article     VARCHAR(32),                                -- 对应条款：第51条第1款
    content         TEXT            NOT NULL,                   -- 检查内容描述
    check_type      VARCHAR(16)     NOT NULL,                   -- AUTO / MANUAL / SEMI_AUTO
    weight          INT             NOT NULL DEFAULT 1,         -- 权重
    -- 评估结果
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING / PASSED / PARTIAL / FAILED / NA
    evidence        TEXT,                                       -- 佐证材料描述
    evidence_files  JSONB,                                      -- 附件文件引用 ["file_id_1", "file_id_2"]
    remark          TEXT,                                       -- 评估备注
    -- 整改
    has_gap         BOOLEAN         NOT NULL DEFAULT FALSE,
    gap_description TEXT,                                       -- 差距描述
    suggestion      TEXT,                                       -- 整改建议
    assignee_id     BIGINT,                                     -- 整改负责人
    due_date        DATE,                                       -- 整改截止日期
    rectify_status  VARCHAR(16)     DEFAULT 'NONE',             -- NONE / PENDING / IN_PROGRESS / VERIFIED / DONE
    -- 自动评估结果（check_type = AUTO时由系统填写）
    auto_result     JSONB,                                      -- 自动检测的详细结果

    CONSTRAINT chk_item_status   CHECK (status IN ('PENDING', 'PASSED', 'PARTIAL', 'FAILED', 'NA')),
    CONSTRAINT chk_item_check    CHECK (check_type IN ('AUTO', 'MANUAL', 'SEMI_AUTO')),
    CONSTRAINT chk_item_rectify  CHECK (rectify_status IN ('NONE', 'PENDING', 'IN_PROGRESS', 'VERIFIED', 'DONE'))
);

CREATE INDEX idx_item_assessment ON assessment_item (assessment_id);
CREATE INDEX idx_item_status     ON assessment_item (status) WHERE status != 'PASSED';
CREATE INDEX idx_item_assignee   ON assessment_item (assignee_id) WHERE assignee_id IS NOT NULL;

CREATE TABLE scan_task (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    source_id       BIGINT          NOT NULL REFERENCES data_source(id),
    scan_type       VARCHAR(16)     NOT NULL,                   -- FULL / INCREMENTAL
    trigger_type    VARCHAR(16)     NOT NULL DEFAULT 'MANUAL',  -- MANUAL / SCHEDULED / API
    cron_expression VARCHAR(64),                                -- 定时任务Cron表达式
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING / RUNNING / COMPLETED / FAILED / CANCELLED
    -- 执行结果
    total_schemas   INT             DEFAULT 0,
    total_tables    INT             DEFAULT 0,
    total_columns   INT             DEFAULT 0,
    sensitive_found INT             DEFAULT 0,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_scan_type   CHECK (scan_type IN ('FULL', 'INCREMENTAL')),
    CONSTRAINT chk_scan_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'API')),
    CONSTRAINT chk_scan_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_scan_source ON scan_task (source_id, created_at DESC);
CREATE INDEX idx_scan_status ON scan_task (status) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE attachment (
    id              BIGSERIAL       PRIMARY KEY,
    biz_type        VARCHAR(32)     NOT NULL,                   -- ASSESSMENT_EVIDENCE / REPORT / EXPORT
    biz_id          BIGINT          NOT NULL,                   -- 关联业务ID
    file_name       VARCHAR(256)    NOT NULL,
    file_size       BIGINT          NOT NULL,
    content_type    VARCHAR(128)    NOT NULL,
    storage_path    VARCHAR(512)    NOT NULL,                   -- 对象存储路径
    checksum        VARCHAR(64)     NOT NULL,                   -- SHA-256
    uploaded_by     BIGINT          NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

CREATE INDEX idx_attachment_biz ON attachment (biz_type, biz_id) WHERE deleted_at IS NULL;

-- 定期分析索引使用情况（建议每周执行）
SELECT
    schemaname, tablename, indexname,
    idx_scan,         -- 索引扫描次数
    idx_tup_read,     -- 索引读取行数
    idx_tup_fetch     -- 索引返回行数
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;

-- 识别未使用的索引（idx_scan = 0 且存在超过7天）
-- 注意：需结合 pg_stat_statements 综合判断

-- 索引膨胀检查
SELECT
    schemaname, tablename, indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 20;

-- 1. 创建租户Schema
CREATE SCHEMA tenant_{id};

-- 2. 在租户Schema中创建所有业务表
-- （通过Flyway迁移脚本自动执行）

-- 3. 授权（应用用户使用受限权限）
GRANT USAGE ON SCHEMA tenant_{id} TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_{id} TO app_user;

-- 由定时任务（pg_cron 或应用层调度器）每月执行
DO $$
DECLARE
    next_month_start DATE := DATE_TRUNC('month', NOW()) + INTERVAL '1 month';
    next_month_end   DATE := DATE_TRUNC('month', NOW()) + INTERVAL '2 months';
    partition_name   TEXT := 'audit_log_' || TO_CHAR(next_month_start, 'YYYY_MM');
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, next_month_start, next_month_end
        );
    END IF;
END $$;

-- 使用 pgcrypto 模块
-- 启用扩展
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 加密函数
CREATE OR REPLACE fn_encrypt_data(plain_data TEXT, key TEXT)
RETURNS BYTEA AS $$
    SELECT pgp_sym_encrypt(plain_data, key);
$$ LANGUAGE SQL IMMUTABLE;

-- 解密函数
CREATE OR REPLACE fn_decrypt_data(encrypted_data BYTEA, key TEXT)
RETURNS TEXT AS $$
    SELECT pgp_sym_decrypt(encrypted_data, key);
$$ LANGUAGE SQL IMMUTABLE;

-- 禁止UPDATE和DELETE（通过角色权限控制）
REVOKE UPDATE, DELETE ON audit_log FROM app_user;
GRANT INSERT, SELECT ON audit_log TO app_user;

-- 哈希链校验函数
CREATE OR REPLACE fn_verify_hash_chain(
    p_start_time TIMESTAMP,
    p_end_time   TIMESTAMP
) RETURNS TABLE (
    id          BIGINT,
    created_at  TIMESTAMP,
    is_valid    BOOLEAN,
    error_desc  TEXT
) AS $$
DECLARE
    rec         RECORD;
    prev_hash   VARCHAR(64) := '0000000000000000000000000000000000000000000000000000000000000000';
    expected    VARCHAR(64);
BEGIN
    FOR rec IN
        SELECT * FROM audit_log
        WHERE created_at BETWEEN p_start_time AND p_end_time
        ORDER BY created_at, id
    LOOP
        expected := encode(sha256(convert_to(
            rec.id || rec.user_id || rec.operation || rec.target_desc ||
            rec.source_ip || rec.result || rec.created_at || prev_hash,
            'UTF-8'
        )), 'hex');

        IF rec.hash != expected THEN
            RETURN QUERY SELECT rec.id, rec.created_at, FALSE,
                'Hash mismatch: expected ' || expected || ', got ' || rec.hash;
        ELSE
            RETURN QUERY SELECT rec.id, rec.created_at, TRUE, NULL;
        END IF;

        prev_hash := rec.hash;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- 即使应用层已做Schema隔离，额外增加RLS作为兜底
ALTER TABLE db_column ENABLE ROW LEVEL SECURITY;

-- 确保只能访问当前租户Schema的数据（通过search_path隐式保证）
-- RLS主要防止代码bug导致的跨租户访问
