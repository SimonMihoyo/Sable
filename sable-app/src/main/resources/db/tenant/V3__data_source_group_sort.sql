-- ============================================================
-- 数据源分组排序
-- ============================================================
-- 分组是 group_name 的派生概念（没有数据源就没有分组），没有独立生命周期，
-- 因此不为它建表：把顺序挂在每行上，同一分组的所有行保持同值，读取时按
-- MIN(group_sort) 排序（容忍个别行被手工改坏）。
--
-- 这样的代价是反范式 —— "同组同值"由应用层维护（DataSourceServiceImpl#resolveGroupSort），
-- 数据库不强制。等分组需要独立属性（描述/颜色/负责人）、需要空分组、或需要改名而不动
-- data_source 时，再升级成 data_source_group 表 + group_id 外键，届时本列即可废弃。
ALTER TABLE data_source ADD COLUMN group_sort INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN data_source.group_sort IS '分组排序权重，越小越靠前；同一分组的所有行同值';
