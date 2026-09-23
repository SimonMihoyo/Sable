package me.kirara.sable.system.dto;

import jakarta.validation.constraints.NotBlank;

/** 创建 / 更新角色请求体。 */
public class RoleSaveRequest {

    @NotBlank(message = "角色名称不能为空")
    private String name;

    @NotBlank(message = "角色显示名不能为空")
    private String displayName;

    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
