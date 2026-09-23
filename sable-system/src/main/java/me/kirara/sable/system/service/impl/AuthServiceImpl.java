package me.kirara.sable.system.service.impl;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.system.dto.LoginRequest;
import me.kirara.sable.system.dto.LoginResponse;
import me.kirara.sable.system.service.AuthService;
import org.springframework.stereotype.Service;

/** 认证服务实现（脚手架占位，待接入 Spring Security / JWT）。 */
@Service
public class AuthServiceImpl implements AuthService {

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO 校验用户名密码、校验 MFA、签发 JWT
        throw new BusinessException(ErrorCode.AUTH_FAILED.getCode(), "认证功能尚未实现");
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // TODO 校验刷新令牌并重新签发
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    @Override
    public void logout(String accessToken) {
        // TODO 将令牌加入吊销列表
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // TODO 校验旧密码并更新 password_hash
    }
}
