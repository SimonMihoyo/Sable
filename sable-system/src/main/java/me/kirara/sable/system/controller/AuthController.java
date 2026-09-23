package me.kirara.sable.system.controller;

import jakarta.validation.Valid;
import me.kirara.sable.common.R;
import me.kirara.sable.system.dto.LoginRequest;
import me.kirara.sable.system.dto.LoginResponse;
import me.kirara.sable.system.dto.PasswordChangeRequest;
import me.kirara.sable.system.dto.TokenRequest;
import me.kirara.sable.system.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口 — /auth */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody TokenRequest request) {
        return R.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestBody TokenRequest request) {
        authService.logout(request.getAccessToken());
        return R.ok();
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        // TODO 从安全上下文获取当前 userId
        authService.changePassword(null, request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }
}
