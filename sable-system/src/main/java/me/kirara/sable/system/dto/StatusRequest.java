package me.kirara.sable.system.dto;

import jakarta.validation.constraints.NotBlank;

/** 通用状态变更请求体（启用 / 停用 / 锁定）。 */
public class StatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
