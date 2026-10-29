package me.kirara.sable.discovery.service;

import me.kirara.sable.discovery.engine.MetadataSnapshot;

/**
 * 资产同步 —— 把一次扫描得到的元数据快照写入 {@code db_schema / db_table / db_column}。
 *
 * <p>按"存在则更新、不存在则插入、快照里没有则删除"同步（全量对齐）。两条硬性约束：</p>
 *
 * <ol>
 *   <li><b>绝不覆盖分类分级字段</b>（{@code sensitivity} / {@code category} /
 *       {@code matched_rule_id} / {@code manual_override} / {@code encryption_req} /
 *       {@code masking_rule}）。这些是人工修正或识别引擎的产出，重新扫描只该刷新结构信息；
 *       把 {@code manual_override} 覆盖成 null 等于把用户的修正悄悄抹掉。</li>
 *   <li><b>删除顺序必须是 列 → 表 → schema</b>：{@code db_column} 引用 {@code db_table}、
 *       {@code db_table} 引用 {@code db_schema}，反过来删会撞外键。</li>
 * </ol>
 */
public interface AssetSyncService {

    /**
     * 把快照同步进目标数据源的资产表。
     *
     * @param sourceId 数据源 ID（快照所属的租户由 {@code TenantContext} 决定）
     * @param snapshot 引擎产出的不可变快照
     */
    void sync(Long sourceId, MetadataSnapshot snapshot);
}
