package me.kirara.sable.system.dto;

/** 更新用户请求体。 */
public class UserUpdateRequest {

    private String email;
    private String phone;
    private String displayName;
    private String department;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
