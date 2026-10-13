package me.kirara.sable.config;

import me.kirara.sable.system.auth.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证模块装配 — 启用 {@link AuthProperties} 配置绑定并暴露密码编码器。
 *
 * <p>仅使用 Spring Security 的 crypto 组件完成密码哈希，不引入完整的 Spring Security 框架。</p>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder(AuthProperties properties) {
        return new BCryptPasswordEncoder(properties.getBcryptStrength());
    }
}
