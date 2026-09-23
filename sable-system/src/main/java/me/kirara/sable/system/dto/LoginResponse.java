package me.kirara.sable.system.dto;

/** 登录 / 刷新令牌响应体。 */
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    /** 过期时间（秒）。 */
    private long expiresIn;
    /** 需要二次 MFA 验证时为 true。 */
    private boolean mfaRequired;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    public boolean isMfaRequired() { return mfaRequired; }
    public void setMfaRequired(boolean mfaRequired) { this.mfaRequired = mfaRequired; }
}
