package me.kirara.sable.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.system.entity.SysUser;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 序列化装配 — 目前只做一件事：给含凭证的实体挂"永不序列化"的兜底规则。
 *
 * <p><b>为什么放在 sable-app 而不是实体上：</b>库模块（含 {@code sable-system}）里
 * {@code spring-boot-starter-web} 是 {@code provided} scope，而 {@code provided} **不传递依赖**，
 * 因此库模块拿不到 Jackson，自然也不该带序列化注解（见根 `AGENTS.md` 的依赖约定）。
 * 序列化是 Web 层关注点，把规则留在启动模块更贴合分层。</p>
 *
 * <p><b>为什么还需要这层兜底：</b>接口的正常出口是 {@code UserResponse} 白名单投影，
 * 这层规则防的是"有人顺手直接返回实体、或把实体嵌进别的响应对象"——那种泄露不会
 * 在编译期被发现，也不会被现有的接口测试拦住。mixin 挂在 {@code ObjectMapper} 上，
 * 对任何层级的序列化都生效。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 给 {@link SysUser} 挂凭证忽略规则。
     *
     * <p>用 {@code mixIn} 而不是直接改造实体：mixin 不改变实体自身，只在序列化视角下
     * 把 {@code passwordHash} / {@code mfaSecret} 当作被忽略的属性。</p>
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer credentialFieldsIgnoredCustomizer() {
        return builder -> builder
                .mixIn(SysUser.class, SysUserCredentialMixin.class)
                .mixIn(DataSource.class, DataSourceCredentialMixin.class);
    }

    /**
     * {@link SysUser} 的序列化视图 —— 只声明"哪些字段不出现"。
     *
     * <p>抽象类本身不被实例化，Jackson 只把它当注解载体。方法签名与实体 getter 对齐即可。</p>
     */
    abstract static class SysUserCredentialMixin {

        /** BCrypt 哈希，绝不序列化。 */
        @JsonIgnore
        abstract String getPasswordHash();

        /** TOTP 密钥，绝不序列化。 */
        @JsonIgnore
        abstract String getMfaSecret();
    }

    /**
     * {@link DataSource} 的序列化视图 —— 口令密文绝不序列化。
     *
     * <p>正常出口是 {@code DataSourceResult} 投影，这层兜底防的是"又有人直接返回实体"。
     * 密文本身解不开，但把与凭证相关的字节发到浏览器这件事不该发生，也不该等到
     * 换密钥那天才发现某个接口一直在吐它。</p>
     */
    abstract static class DataSourceCredentialMixin {

        /** AES-GCM 口令密文，绝不序列化。 */
        @JsonIgnore
        abstract byte[] getPasswordEnc();
    }
}
