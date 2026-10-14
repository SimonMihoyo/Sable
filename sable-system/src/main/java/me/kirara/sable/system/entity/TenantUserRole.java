package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;

/**
 * 租户用户角色关联表 public.tenant_user_role
 *
 * <p>一个用户在某租户下可以同时持有多个角色，故角色独立成表，一行一个「用户-角色」授予。
 * {@code tenant_user} 仍只描述成员关系（部门、状态），不重复存储。</p>
 *
 * <p>本表无软删除列：重新分配角色时按「先清空该用户在租户下的授予、再按入参重建」处理，
 * 避免残留的已撤销角色被误装配进令牌。</p>
 */
@TableName("tenant_user_role")
public class TenantUserRole extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long roleId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
