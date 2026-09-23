package me.kirara.sable.system.service;

import me.kirara.sable.system.dto.LoginRequest;
import me.kirara.sable.system.dto.LoginResponse;

/** 认证服务 — 登录、Token 刷新、登出、改密。 */
public interface AuthService {

    /** 用户登录，返回访问令牌与刷新令牌。 */
    LoginResponse login(LoginRequest request);

    /** 使用刷新令牌换取新的访问令牌。 */
    LoginResponse refresh(String refreshToken);

    /** 退出登录，吊销令牌。 */
    void logout(String accessToken);

    /** 修改当前用户密码。 */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
