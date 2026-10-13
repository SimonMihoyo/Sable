package me.kirara.sable.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.kirara.sable.system.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JacksonConfig} 的兜底规则测试 —— 直接序列化 {@link SysUser} 实体，
 * 断言凭证字段不出网。
 *
 * <p>接口的正常出口是 {@code UserResponse}，这里测的是"有人绕开 DTO 直接返回实体"的情形。</p>
 */
class JacksonConfigTest {

    /** 按 {@link JacksonConfig} 的定制方式构造一个 ObjectMapper，与运行时行为一致。 */
    private ObjectMapper configuredObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().credentialFieldsIgnoredCustomizer().customize(builder);
        return builder.build();
    }

    private SysUser userWithCredentials() {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("alice");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        user.setStatus("ACTIVE");
        return user;
    }

    @Test
    @DisplayName("直接序列化实体时 passwordHash 与 mfaSecret 被忽略")
    void credentialsIgnoredWhenSerializingEntity() throws Exception {
        String json = configuredObjectMapper().writeValueAsString(userWithCredentials());

        assertFalse(json.contains("passwordHash"), "实体序列化不得含 passwordHash: " + json);
        assertFalse(json.contains("mfaSecret"), "实体序列化不得含 mfaSecret: " + json);
        assertFalse(json.contains("$2a$10$abcdefghijklmnopqrstuv"));
        assertFalse(json.contains("JBSWY3DPEHPK3PXP"));
        // 兜底规则不能把整个实体变成空对象，正常字段仍应出现
        assertTrue(json.contains("\"username\":\"alice\""), json);
        assertTrue(json.contains("\"status\":\"ACTIVE\""), json);
    }

    @Test
    @DisplayName("实体嵌套在别的响应对象里时同样被忽略")
    void credentialsIgnoredWhenNested() throws Exception {
        String json = configuredObjectMapper()
                .writeValueAsString(new Nested(userWithCredentials()));

        assertFalse(json.contains("passwordHash"), json);
        assertFalse(json.contains("mfaSecret"), json);
        assertTrue(json.contains("\"username\":\"alice\""), json);
    }

    /** 模拟"把实体嵌进别的响应对象"的写法。 */
    static class Nested {

        private final SysUser user;

        Nested(SysUser user) {
            this.user = user;
        }

        public SysUser getUser() {
            return user;
        }
    }

    /**
     * 校验定制器真的被 Spring Boot 的 Jackson 自动装配采纳。
     *
     * <p>上面两个用例手工调用 {@code customize()}，只能证明规则本身有效；
     * 这个用例跑一遍真实的自动装配，证明**运行时那个 {@code ObjectMapper}** 也带上了规则
     * —— 否则规则写得再对，没人调用也是白搭。</p>
     */
    @Test
    @DisplayName("Spring 自动装配出的 ObjectMapper 同样忽略凭证字段")
    void customizerIsAppliedByAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertNotNull(mapper);

                    String json = mapper.writeValueAsString(userWithCredentials());

                    assertFalse(json.contains("passwordHash"), "自动装配的 ObjectMapper 不得含 passwordHash: " + json);
                    assertFalse(json.contains("mfaSecret"), "自动装配的 ObjectMapper 不得含 mfaSecret: " + json);
                    assertTrue(json.contains("\"username\":\"alice\""), json);
                });
    }
}
