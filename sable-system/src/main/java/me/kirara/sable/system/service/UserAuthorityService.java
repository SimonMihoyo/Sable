package me.kirara.sable.system.service;

import me.kirara.sable.system.auth.UserAuthority;

/**
 * 用户租户 / 角色归属服务 — 为签发令牌提供"这个人在哪个租户、什么角色"。
 *
 * <p>为什么要单独一层：这套归属关系来自 {@code tenant_user} 与 {@code role} 两张表，
 * 而认证流程只关心结果。把它抽出来，认证服务不必直接依赖两个 Mapper，
 * 迁移多租户选择策略时也只改这一处。</p>
 */
public interface UserAuthorityService {

    /**
     * 解析用户的租户与角色归属（按"最早加入的租户"选取）。
     *
     * <p>供<b>签发令牌</b>使用：登录接口没有租户参数，只能替用户挑一个默认租户。</p>
     *
     * @param userId 用户 ID，为空时返回空归属
     * @return 归属结果，永不返回 {@code null}；无归属时 tenantId 为 null、角色与权限为空列表
     */
    UserAuthority resolve(Long userId);

    /**
     * 解析用户在<b>指定租户</b>下的角色与权限归属。
     *
     * <p>供<b>已登录请求</b>使用：此时租户已经确定（来自令牌的 {@code tid}，或超级管理员的
     * {@code X-Tenant-Id}），按当前租户装配才与实际访问的数据范围一致。</p>
     *
     * <p>与 {@link #resolve(Long)} 的差别只在"租户从哪来"：本方法不查 {@code tenant_user}
     * 的成员关系，也不会因为用户属于多个租户而打 WARN。传 null 租户时返回空权限 ——
     * 这是"系统级用户"的正常形态，不是错误。</p>
     *
     * @param userId   用户 ID，为空时返回空归属
     * @param tenantId 租户 ID，为空时返回空归属
     * @return 归属结果，永不返回 {@code null}
     */
    UserAuthority resolveInTenant(Long userId, Long tenantId);
}
