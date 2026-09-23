package me.kirara.sable.system.dto;

import java.util.List;

/** 设置角色权限请求体。 */
public class RolePermissionRequest {

    private List<String> permissions;

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
