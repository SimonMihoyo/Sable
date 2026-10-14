-- ============================================================================
-- Sablé 数据合规管理系统 — 应用级基线迁移 V1（public schema）
-- ============================================================================
-- 目标数据库：PostgreSQL 14+
--
-- ⚠️ 本文件是**不可变基线**，一旦被任何环境应用过就不要再编辑。
--    Flyway 会记录每个已应用迁移的校验和（checksum），事后修改本文件会让
--    所有已应用过的环境在下次启动时报 "Migration checksum mismatch"，
--    只能靠 repair 或手工改元数据表才能恢复。
--
-- 因此，**任何后续表结构变更都必须新建一个迁移文件**，而不是改这里：
--    db/migration/V2__add_xxx.sql
--    db/migration/V3__alter_yyy.sql
--    命名必须是 V<递增整数>__<小写下划线描述>.sql
--    一个迁移一个目的；已提交的 V2 同样不可再改，只能再加 V3。
--
-- 想手工执行本文件（不带 Flyway）仍然可以：
--    psql -U <user> -d <db> -v ON_ERROR_STOP=1 -f <本文件>
-- 但那样不会写入 flyway_schema_history，下次启动 Flyway 会视为「非空库」
-- 并依赖 spring.flyway.baseline-on-migrate 才能接上（见 application.yml）。
-- ============================================================================
--
-- 【本文件只管 public schema —— 多租户改造后的职责边界】
--
-- 全库分两层，对应两个 Flyway 扫描路径，**不要混**：
--
--   db/migration/  ← 本文件所在       应用级：每个数据库只执行一次，
--                                    建 public schema 下的 9 张公共表
--                                    （租户 / 用户 / 角色 / 系统配置…）
--
--   db/tenant/     ← 租户级           租户级：**每个租户 schema 各执行一次**，
--                                    建 12 张业务表（数据源 / 分类规则 / 审计日志…）
--
-- 为什么必须分开：db/migration/ 的所有环境都会执行，而租户表要按租户
-- 逐个建到 `tenant_{id}` 里；把两者混在同一个路径下，Flyway 会把这 12 张表
-- 一并建到 public，之后租户请求就会**静默读到 public 里的那份数据**——
-- 既不报错也不隔离。租户表的建表逻辑见 db/tenant/V1__tenant_schema.sql。
--
-- 本文件的内容源自改造前的 db/schema.sql（已由 `git mv` 保留历史），
-- 那段"自动抽取自数据库设计文档"的说法早已不准确 —— 该文件被人工修过 6 处
-- 才可执行，且此后又经历了 password_history / token_revocation /
-- tenant_user_role 三轮增删，**它才是事实来源，文档向它对齐**。
-- ============================================================================


-- ============================================================
-- 一、系统公共域（public schema）—— 9 张表
-- ============================================================

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

-- 密码历史 — 记录用户曾经使用过的密码哈希，用于阻止重复使用近期密码
-- （策略：不得与最近 N 次相同，N 由 sable.auth.password-history-size 配置，默认 3）
-- 追加写、不更新，故只有 changed_at 一个时间戳列，也没有 deleted_at
CREATE TABLE public.password_history (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES public.sys_user(id),
    password_hash   VARCHAR(256)    NOT NULL,                   -- BCrypt哈希
    changed_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_history_user ON public.password_history (user_id, changed_at DESC);

-- 令牌吊销表 — 登出 / 强制下线时按 jti 记一行，认证入口逐次比对本表
-- 为什么不用 iat：iat 只有秒级精度，同一秒内的两枚令牌无法区分，会误伤
-- 追加写、行不可变，故只有 revoked_at 一个时间戳列，也没有 deleted_at
-- 令牌自然过期后本表记录即失去意义，由定时任务清理（见 TokenRevocationCleanupJob）
CREATE TABLE public.token_revocation (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES public.sys_user(id),
    jti             VARCHAR(64)     NOT NULL,                   -- JWT 标准声明 jti
    token_type      VARCHAR(16)     NOT NULL,                   -- access / refresh
    expires_at      TIMESTAMP       NOT NULL,                   -- 令牌自身过期时间
    revoked_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    reason          VARCHAR(32)     NOT NULL DEFAULT 'LOGOUT',

    CONSTRAINT uq_token_revocation_jti UNIQUE (jti),
    CONSTRAINT chk_token_revocation_type CHECK (token_type IN ('access', 'refresh'))
);

-- 清理任务按 expires_at 扫过期行
CREATE INDEX idx_token_revocation_expires ON public.token_revocation (expires_at);
-- 按用户强制下线时按 user_id 查
CREATE INDEX idx_token_revocation_user    ON public.token_revocation (user_id);

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

-- 用户在某租户下被授予的角色 —— 多对多，一个用户可同时持有多个角色。
-- 与 tenant_user 的分工：后者描述"某用户是不是某租户的成员"（部门、状态挂在这里，
-- 一行一个成员关系）；角色可能不止一个，若挤进 tenant_user 就得为每个角色复制一行，
-- 部门与状态随之重复存储、改一处要保证 N 行一致，故拆到本表。
CREATE TABLE public.tenant_user_role (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL REFERENCES public.tenant(id),
    user_id         BIGINT          NOT NULL REFERENCES public.sys_user(id),
    role_id         BIGINT          NOT NULL REFERENCES public.role(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tenant_user_role UNIQUE (tenant_id, user_id, role_id)
);

-- (tenant_id, user_id) 的组合查询由上面的唯一约束索引覆盖，无需重复建索引
CREATE INDEX idx_tenant_user_role_user ON public.tenant_user_role (user_id);
CREATE INDEX idx_tenant_user_role_role ON public.tenant_user_role (role_id);

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


-- ============================================================
-- 二、数据库级对象 —— 扩展与函数
-- ============================================================
-- 这些对象属于**数据库**而不是某个 schema，因此只能在应用级迁移里建一次，
-- 且函数体里的 `audit_log` 保持**不限定 schema**：它在调用时按当前
-- search_path 解析，租户连接（search_path = tenant_{id}, public）下自然落到
-- 本租户的审计日志表上。若在这里写死 public.audit_log，租户校验函数就查错了地方。

-- 使用 pgcrypto 模块
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 加密函数
CREATE OR REPLACE FUNCTION fn_encrypt_data(plain_data TEXT, key TEXT)
RETURNS BYTEA AS $$
    SELECT pgp_sym_encrypt(plain_data, key);
$$ LANGUAGE SQL IMMUTABLE;

-- 解密函数
CREATE OR REPLACE FUNCTION fn_decrypt_data(encrypted_data BYTEA, key TEXT)
RETURNS TEXT AS $$
    SELECT pgp_sym_decrypt(encrypted_data, key);
$$ LANGUAGE SQL IMMUTABLE;

-- 哈希链校验函数
-- 注意：函数体里的 audit_log 故意不写 schema 前缀 —— 见本节开头说明。
CREATE OR REPLACE FUNCTION fn_verify_hash_chain(
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
        WHERE audit_log.created_at BETWEEN p_start_time AND p_end_time
        ORDER BY audit_log.created_at, audit_log.id
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

-- 禁止 UPDATE / DELETE（通过受限应用角色控制）
-- 注意：app_user 角色当前既未在本脚本中创建，应用也默认以库属主身份连接，
-- 故以下授权暂时不生效，待「多租户 + 受限应用角色」落地时再启用（需超级用户创建角色）。
-- REVOKE UPDATE, DELETE ON audit_log FROM app_user;
-- GRANT INSERT, SELECT ON audit_log TO app_user;


-- ============================================================
-- 【运维片段】以下为定期巡检查询，不属于初始化 DDL，故注释保留。
-- 注意：pg_stat_user_indexes 的列名是 relname / indexrelname（不是 tablename / indexname），
--       且原写法会因列名不存在直接报错；如需执行请复制出去单独跑。
-- ============================================================
-- -- 定期分析索引使用情况（建议每周执行）
-- SELECT
--     schemaname, relname AS tablename, indexrelname AS indexname,
--     idx_scan,         -- 索引扫描次数
--     idx_tup_read,     -- 索引读取行数
--     idx_tup_fetch     -- 索引返回行数
-- FROM pg_stat_user_indexes
-- ORDER BY idx_scan ASC;
--
-- -- 识别未使用的索引（idx_scan = 0 且存在超过7天）
-- -- 注意：需结合 pg_stat_statements 综合判断
--
-- -- 索引膨胀检查
-- SELECT
--     schemaname, relname AS tablename, indexrelname AS indexname,
--     pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
-- FROM pg_stat_user_indexes
-- ORDER BY pg_relation_size(indexrelid) DESC
-- LIMIT 20;
