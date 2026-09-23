package me.kirara.sable.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 创建租户请求体。 */
public class TenantCreateRequest {

    @NotBlank(message = "租户名称不能为空")
    private String name;

    @NotBlank(message = "租户编码不能为空")
    private String code;

    /** BASIC / PROFESSIONAL / ENTERPRISE，缺省 BASIC。 */
    private String planType;

    private String contactName;

    @Email(message = "邮箱格式不正确")
    private String contactEmail;

    private String contactPhone;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
