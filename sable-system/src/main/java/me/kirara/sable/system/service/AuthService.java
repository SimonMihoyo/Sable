package me.kirara.sable.system.service;

import me.kirara.sable.system.dto.LoginRequest;
import me.kirara.sable.system.dto.LoginResponse;

/** 认证服务 — 登录、Token 刷新、登出、改密。 */
public interface AuthService {

    /** 用户登录，返回访问令牌与刷新令牌。 */
    LoginResponse login(LoginRequest request);

    /** 使用刷新令牌换取新的访问令牌。 */
    LoginResponse refresh(String refreshToken);

    /**
     * 退出登录 — 吊销访问令牌，并可选吊销刷新令牌。
     *
     * <p>幂等：未携带令牌、令牌已过期或非法时同样正常返回，不暴露令牌状态。</p>
     *
     * @param accessToken  请求头中的访问令牌，可为空
     * @param refreshToken 请求体中的刷新令牌，可为空；为空时刷新令牌在有效期内仍可换新令牌
     */
    void logout(String accessToken, String refreshToken);

    /** 修改当前用户密码。 */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
