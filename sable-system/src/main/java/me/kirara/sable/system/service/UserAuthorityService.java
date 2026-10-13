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
     * 解析用户的租户与角色归属。
     *
     * @param userId 用户 ID，为空时返回空归属
     * @return 归属结果，永不返回 {@code null}；无归属时 tenantId 为 null、角色为空列表
     */
    UserAuthority resolve(Long userId);
}
