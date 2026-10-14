package me.kirara.sable.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;

import java.util.List;

/** SysUser 业务服务。 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 在指定租户下创建用户。
     *
     * <p><b>密码哈希在服务内完成</b>：调用方传入的是明文请求体，不允许自行拼装实体后调
     * {@code save()} —— 那样迟早会有人把明文直接写进 {@code password_hash} 列。</p>
     *
     * <p>同时建立租户归属（{@code tenant_user}，含 {@code department}）并依据
     * {@code request.roleIds} 授予角色（{@code tenant_user_role}）。三步在同一事务内完成，
     * 任一步失败都不会留下"账号建了却进不了任何租户"的半成品。</p>
     *
     * @param tenantId 新用户所属租户，由调用方从租户上下文取得
     * @param request  创建请求（用户名、明文密码必填）
     * @return 已落库的用户实体，{@code id} 已回填，{@code passwordHash} 为 BCrypt 哈希
     */
    SysUser createUser(Long tenantId, UserCreateRequest request);

    /**
     * 覆盖式设置某用户在某租户下的角色集合。
     *
     * <p>"覆盖"而非"增量"：先清空该用户在本租户下的全部角色授予，再按入参重建。
     * 传空列表即收回其全部角色。之所以物理删除而不是软删除，是为了让"被撤销的角色"
     * 立刻从令牌装配中消失，不留可被误读的残留记录。</p>
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param roleIds  目标角色 ID 列表；可为 null 或空（表示不保留任何角色）
     */
    void assignRoles(Long tenantId, Long userId, List<Long> roleIds);
}
