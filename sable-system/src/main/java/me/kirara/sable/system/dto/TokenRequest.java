package me.kirara.sable.system.dto;

/** 令牌请求体 — 刷新 / 登出接口使用。 */
public class TokenRequest {

    private String accessToken;
    private String refreshToken;

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
