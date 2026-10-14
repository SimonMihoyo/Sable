-- ============================================================================
-- Sablé 数据合规管理系统 — 开发环境种子数据（Flyway 可重复迁移）
-- ============================================================================
-- 目的只有一个：**让本地库开箱即有能登录的账号**，前端不必先手工插数据。
--
-- ⚠️ 本文件只能用于本地 / 开发环境，绝不允许进入生产库。
--
-- 为什么放在 db/dev/ 而不是 db/migration/：
--   db/migration/ 是 Flyway 的常规扫描路径，**所有环境都会执行**。
--   把这批"密码公开的演示账号"放进那里，等于给生产库预置了一批人人知道的凭证 ——
--   对合规产品而言这是最不该出现的缺陷。因此本文件单独放在 db/dev/，
--   只有 dev profile 才会把它加进扫描路径（见 application-dev.yml）；
--   不带 dev profile 启动时，Flyway 根本看不到本文件。
--
-- 启用方式（任选其一）：
--   SPRING_PROFILES_ACTIVE=dev java -jar sable-app/target/sable-app-0.0.1-SNAPSHOT.jar
--   ./mvnw -pl sable-app -am spring-boot:run -Dspring-boot.run.profiles=dev
--   IntelliJ 运行配置里把 Active profiles 填成 dev
--
-- 为什么用可重复迁移（R__）而不是版本迁移（V__）：
--   1) R__ 不带版本号，永远不会和 db/migration/ 将来的 V2、V3… 撞号
--      （若这里用 V2，将来 db/migration 出现 V2 时 Flyway 会因"版本重复"直接拒绝启动）；
--   2) 文件一旦被修改，Flyway 会重新执行一次 —— 正好用来把开发数据复位到已知状态。
--   代价是**本文件必须幂等**：所有写入都带 ON CONFLICT，重复执行既不报错也不产生重复行。
--
-- 复查本文件的效果（纯查询，不改数据）：
--   SELECT u.username, u.status, t.code AS tenant, r.name AS role, tur.department
--   FROM sys_user u
--   LEFT JOIN tenant_user tur     ON tur.user_id = u.id
--   LEFT JOIN tenant t            ON t.id = tur.tenant_id
--   LEFT JOIN tenant_user_role tt ON tt.user_id = u.id
--   LEFT JOIN role r              ON r.id = tt.role_id
--   WHERE u.username IN ('alice', 'bob', 'carol')
--   ORDER BY u.username, r.name;
--
-- 想再加业务演示数据（数据源、分类规则、审计日志…）请另开一个 R__ 文件，
-- 本文件只负责"能登进去"这一件事。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 演示租户
-- ---------------------------------------------------------------------------
-- config 的形状取自 V1 中 tenant.config 列的注释样例（配额 + 特性开关）。
-- 目前没有任何代码读取它，属于"先把数据摆上"的占位；等配额校验落地后再对接。
INSERT INTO public.tenant (name, code, plan_type, status, contact_name, contact_email, config)
VALUES (
    '示例科技有限公司',
    'example_tech',
    'PROFESSIONAL',
    'ACTIVE',
    '张三',
    'admin@example.com',
    '{
       "max_data_sources": 10,
       "max_users": 20,
       "storage_quota_gb": 50,
       "audit_log_retention_days": 180,
       "features": {
         "anomaly_detection": false,
         "custom_rules": true,
         "api_access": true
       }
     }'::jsonb
)
ON CONFLICT (code) DO UPDATE
SET name          = EXCLUDED.name,
    plan_type     = EXCLUDED.plan_type,
    status        = EXCLUDED.status,
    contact_name  = EXCLUDED.contact_name,
    contact_email = EXCLUDED.contact_email,
    config        = EXCLUDED.config,
    updated_at    = NOW(),
    deleted_at    = NULL;


-- ---------------------------------------------------------------------------
-- 2. 预置角色（is_system = TRUE：系统预置角色不允许删除）
-- ---------------------------------------------------------------------------
-- 权限码**逐字取自 V1 中 role.permissions 列的注释样例**，不额外发明。
-- 提醒：接口级鉴权（@PreAuthorize 之类）与权限码字典都还没落地，此刻 permissions
-- 只是一份数据，不会真的拦住任何请求。前端可以先用它渲染菜单/按钮的可见性，
-- 等服务端真做校验时，这份数据就是现成的对照。
INSERT INTO public.role (tenant_id, name, display_name, description, is_system, permissions)
SELECT t.id, v.name, v.display_name, v.description, TRUE, v.permissions
FROM public.tenant t
CROSS JOIN (VALUES
    ('SUPER_ADMIN',        '超级管理员', '租户内全部权限（含系统管理）',
     '["data_source:read","data_source:write","audit_log:read","audit_log:export","assessment:read","assessment:write","report:read","report:export","system:manage"]'::jsonb),
    ('COMPLIANCE_OFFICER', '合规管理员', '合规数据读写与报告导出，不含系统管理',
     '["data_source:read","data_source:write","audit_log:read","audit_log:export","assessment:read","assessment:write","report:read","report:export"]'::jsonb),
    ('AUDITOR',            '审计员',     '只读：查数据源、审计日志、评估与报告',
     '["data_source:read","audit_log:read","assessment:read","report:read"]'::jsonb)
) AS v(name, display_name, description, permissions)
WHERE t.code = 'example_tech'
ON CONFLICT (tenant_id, name) DO UPDATE
SET display_name = EXCLUDED.display_name,
    description  = EXCLUDED.description,
    is_system    = EXCLUDED.is_system,
    permissions  = EXCLUDED.permissions,
    updated_at   = NOW(),
    deleted_at   = NULL;


-- ---------------------------------------------------------------------------
-- 3. 可登录账号（密码统一为 Sable@2026）
-- ---------------------------------------------------------------------------
-- 密码哈希是 BCrypt（强度 10，与 sable.auth.bcrypt-strength 一致），来自密码明文
-- Sable@2026。重复执行会把这些账号**复位**：密码还原、解锁、清除软删除。
--
-- ⚠️ carol 的 mfa_enabled 必须是 FALSE。
--    AuthServiceImpl.login 目前遇到 mfa_enabled = TRUE 会**直接抛 MFA_REQUIRED 拒绝登录**
--    （TOTP 校验尚未实现，代码里有对应 TODO）。也就是说"开着 MFA"的账号当下根本进不去，
--    拿它当演示账号只会在前端那里得到一个必然失败的登录。
--    要验证 MFA 分支时，把下面 carol 那一行的 FALSE 改成 TRUE 再重启即可 ——
--    本文件是 R__ 可重复迁移，文件一改就会重跑；届时 carol 会稳定返回 40004。
INSERT INTO public.sys_user (username, password_hash, email, display_name, status,
                             mfa_enabled, login_fail_count, locked_until)
VALUES
    ('alice', '$2a$10$55RXdth69ZWsqVYzCH5VHu8dlz4jw9hU2BiF1FmetfaIVkJgBpUUW', 'alice@example.com', '演示管理员', 'ACTIVE', FALSE, 0, NULL),
    ('bob',   '$2a$10$55RXdth69ZWsqVYzCH5VHu8dlz4jw9hU2BiF1FmetfaIVkJgBpUUW', 'bob@example.com',   '演示审计员', 'ACTIVE', FALSE, 0, NULL),
    ('carol', '$2a$10$55RXdth69ZWsqVYzCH5VHu8dlz4jw9hU2BiF1FmetfaIVkJgBpUUW', 'carol@example.com', '演示合规官', 'ACTIVE', FALSE, 0, NULL)
ON CONFLICT (username) DO UPDATE
SET password_hash       = EXCLUDED.password_hash,
    email               = EXCLUDED.email,
    display_name        = EXCLUDED.display_name,
    status              = EXCLUDED.status,
    mfa_enabled         = EXCLUDED.mfa_enabled,
    login_fail_count    = 0,
    locked_until        = NULL,
    password_changed_at = NOW(),
    updated_at          = NOW(),
    deleted_at          = NULL;


-- ---------------------------------------------------------------------------
-- 4. 租户成员关系（tenant_user）
-- ---------------------------------------------------------------------------
-- tenant_user 只表示"该用户是不是该租户的成员"（含数据范围维度 department），
-- 角色授予在下一节的 tenant_user_role 里。登录时用户的默认租户由
-- UserAuthorityServiceImpl 按 tenant_user 记录选取。
INSERT INTO public.tenant_user (tenant_id, user_id, department, status)
SELECT t.id, u.id, v.department, 'ACTIVE'
FROM public.tenant t
JOIN (VALUES
    ('alice', '技术部'),
    ('bob',   '合规部'),
    ('carol', '审计部')
) AS v(username, department) ON TRUE
JOIN public.sys_user u ON u.username = v.username
WHERE t.code = 'example_tech'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET department = EXCLUDED.department,
    status     = EXCLUDED.status,
    updated_at = NOW(),
    deleted_at = NULL;


-- ---------------------------------------------------------------------------
-- 5. 角色授予（tenant_user_role，多对多）
-- ---------------------------------------------------------------------------
-- alice 故意给了**两个**角色：这是多角色模型在真实库上的一条活样本，
-- 登录后令牌的 roles 声明会是 ["SUPER_ADMIN", "COMPLIANCE_OFFICER"]，
-- 可用来验证多角色装配与"一个用户多个角色"的权限合并。
-- 授予记录没有软删除列（撤销即物理删除），所以冲突时 DO NOTHING 即可。
INSERT INTO public.tenant_user_role (tenant_id, user_id, role_id)
SELECT t.id, u.id, r.id
FROM public.tenant t
JOIN (VALUES
    ('alice', 'SUPER_ADMIN'),
    ('alice', 'COMPLIANCE_OFFICER'),
    ('bob',   'AUDITOR'),
    ('carol', 'COMPLIANCE_OFFICER')
) AS v(username, role_name) ON TRUE
JOIN public.sys_user u ON u.username = v.username
JOIN public.role r     ON r.tenant_id = t.id AND r.name = v.role_name
WHERE t.code = 'example_tech'
ON CONFLICT (tenant_id, user_id, role_id) DO NOTHING;
