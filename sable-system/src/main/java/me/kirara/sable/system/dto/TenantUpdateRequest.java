package me.kirara.sable.system.dto;

/** 更新租户请求体。 */
public class TenantUpdateRequest {

    private String name;
    private String planType;
    private String contactName;
    private String contactEmail;
    private String contactPhone;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
