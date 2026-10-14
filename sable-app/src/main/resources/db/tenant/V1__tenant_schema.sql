-- ============================================================================
-- Sablé 数据合规管理系统 — 租户级基线迁移 V1（tenant_{id} schema）
-- ============================================================================
-- 目标数据库：PostgreSQL 14+
--
-- 【本文件与 db/migration/ 的分工】
--
--   db/migration/V1__init_schema.sql   应用级：public 下的 9 张公共表 + 加密/校验函数
--   db/tenant/V1__tenant_schema.sql    租户级：tenant_{id} 下的 12 张业务表 ← 本文件
--
-- 本文件**不会被应用启动时的 Flyway 执行**（应用那条链路只扫 db/migration）。
-- 它由 TenantSchemaProvisioner 在「建租户」时按租户逐个执行一次：
--   数据库连接 → 目标 schema = tenant_{id} → locations = classpath:db/tenant → migrate()
-- 每个租户 schema 里会留下自己的 flyway_schema_history，独立演进。
--
-- 【为什么语句一律不写 schema 前缀】
-- Flyway 以 defaultSchema = tenant_{id} 运行，未限定名的对象落在该 schema 里。
-- 一旦写成 public.xxx，建表就会跑到公共 schema 去；而写成 tenant_1.xxx 会让
-- 本文件对所有租户失效。**保持全篇无前缀**是这份文件唯一要守的纪律。
--
-- 【新增租户表怎么办】
-- 在本目录下按版本号往后加（V2__add_report_table.sql …）。已在跑的租户不会自动
-- 跟上：租户 schema 的迁移只在「建租户」或「启动期补齐（sable.tenant.provision-on-startup）」
-- 时执行，后者的 migrate() 会把这批新脚本一并应用。见 AGENTS.md「多租户」一节。
--
-- ⚠️ 本文件同样遵守「已提交不可改」的规则：它一旦被某个环境执行过，
--    改动会让该环境的租户在下次 migrate 时报 checksum mismatch。
-- ============================================================================

-- ============================================================
-- 一、数据发现域（数据源 → 库 → 表 → 字段 四级资产树）
-- ============================================================

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

-- 即使应用层已做 Schema 隔离，额外增加 RLS 作为兜底（对应设计文档「RLS 兜底」一节）。
-- ⚠️ 请注意本行**当前是空转的**：只 ENABLE 而没有任何 POLICY，配合"以库属主身份连接"
--    正好等于不设防（表属主与超级用户不受 RLS 约束）。它真正生效要等到引入受限应用角色
--    （app_user + FORCE ROW LEVEL SECURITY + 按 current_schema() 写 POLICY）那一天。
--    保留在这里是为了不丢掉设计意图，**不要把它当作已经有了行级兜底**。
ALTER TABLE db_column ENABLE ROW LEVEL SECURITY;

-- ============================================================
-- 二、分类分级域
-- ============================================================

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

-- ============================================================
-- 三、审计域（按月分区）
-- ============================================================

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

-- ⚠️ 已挂的分区不覆盖当下时间（今天是 2026-10），也就是说**此刻往 audit_log 写入会失败**
--    （no partition of relation "audit_log" found for row）。设计文档 §6.1 的方案是
--    「定时任务每月25号自动创建下月分区」，该任务尚未实现，所以下面这段是唯一的分区来源：
--    它在**每次租户迁移时**执行一遍，为本租户补建"下个月"的分区。
--    这只是权宜之计 —— 一个租户补到一个月的分区，之后仍会断。真正的修法（补一个 DEFAULT
--    分区，或把月度任务做出来）需要单独排期，本次多租户改造**刻意不顺手改语义**。
DO $$
DECLARE
    next_month_start DATE := DATE_TRUNC('month', NOW()) + INTERVAL '1 month';
    next_month_end   DATE := DATE_TRUNC('month', NOW()) + INTERVAL '2 months';
    partition_name   TEXT := 'audit_log_' || TO_CHAR(next_month_start, 'YYYY_MM');
BEGIN
    -- 判断存在性必须**同时**限定 schema：原写法只比 relname，第一个租户建过之后
    -- 其余租户就会全部被跳过，各自缺分区。租户场景下这是必踩的坑。
    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = partition_name
          AND n.nspname = current_schema()
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, next_month_start, next_month_end
        );
    END IF;
END $$;

-- 索引（每个分区自动继承）
CREATE INDEX idx_audit_user_time   ON audit_log (user_id, created_at DESC);
CREATE INDEX idx_audit_operation   ON audit_log (operation, created_at DESC);
CREATE INDEX idx_audit_target      ON audit_log (target_desc);
CREATE INDEX idx_audit_level       ON audit_log (target_level) WHERE target_level IS NOT NULL;
CREATE INDEX idx_audit_ip          ON audit_log (source_ip);
CREATE INDEX idx_audit_hash        ON audit_log (hash);

-- ============================================================
-- 四、告警域
-- ============================================================

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

-- ============================================================
-- 五、合规评估域
-- ============================================================

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

-- ============================================================
-- 六、扫描任务与文件
-- ============================================================

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

-- ============================================================
-- 【待办】本 schema 里还缺 report / report_schedule 两张表
-- ============================================================
-- sable-report 模块的 ReportMapper / ReportScheduleMapper 与对应实体都存在，
-- 但数据库设计文档 §3.2 没有给出这两张表的定义，V1 里也没有 ——
-- 一旦有代码真的查它们，会得到 "relation \"report\" does not exist"。
-- 补表需要先确定字段（对外报告清单、导出记录、调度配置），属于独立的一步，
-- 本次多租户改造不代作决定。
--
-- 【模板】受限应用角色的授权（等引入 app_user 后启用）
-- GRANT USAGE ON SCHEMA <本 schema> TO app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA <本 schema> TO app_user;
