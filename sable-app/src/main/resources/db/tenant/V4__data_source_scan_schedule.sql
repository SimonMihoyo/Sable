-- ============================================================
-- 定时扫描配置 —— 每个数据源一条调度
-- ============================================================
-- 调度配置挂在数据源上（一个数据源最多一个定时扫描），不单独建表：
-- 它没有独立生命周期，也没有自己的属性，只有 cron + 开关 + 下次执行时间三件事。
--
-- schedule_next_run_at 是"下一次该跑的时刻"，由应用在保存配置时算好、每次触发后推进。
-- 存"下次执行时间"而不是"上次执行时间"，是为了让调度判断只有一句
--   WHERE schedule_enabled AND schedule_next_run_at <= now()
-- 同时天然避免"补跑一堆积压的槽位"：停机期间错过的时间点只会触发一次，而不是回放 N 次。
ALTER TABLE data_source
    ADD COLUMN schedule_cron        VARCHAR(64),
    ADD COLUMN schedule_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN schedule_next_run_at TIMESTAMP;

COMMENT ON COLUMN data_source.schedule_cron        IS '定时扫描 cron（6 段，秒 分 时 日 月 周）';
COMMENT ON COLUMN data_source.schedule_enabled     IS '是否启用定时扫描';
COMMENT ON COLUMN data_source.schedule_next_run_at IS '下次执行时间；关闭时为空，每次触发后推进';
