package me.kirara.sable.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import me.kirara.sable.common.R;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.common.util.BearerTokens;
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

    /**
     * 退出登录。
     *
     * <p>访问令牌从 {@code Authorization} 头取（与其它接口一致），刷新令牌不在请求头里，
     * 只能由调用方放进可选的请求体。请求体为空时只吊销访问令牌。</p>
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request,
                          @RequestBody(required = false) TokenRequest body) {
        authService.logout(BearerTokens.resolve(request.getHeader(BearerTokens.HEADER)),
                body == null ? null : body.getRefreshToken());
        return R.ok();
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        // 用户身份由 JwtAuthenticationFilter 从 Authorization 头解析后写入 UserContext
        authService.changePassword(UserContext.getUserId(), request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }
}
