package me.kirara.sable.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档装配 —— 声明 OpenAPI 元信息与认证方式。
 *
 * <p>SpringDoc 会自动扫描各模块的 Controller 生成规范，本类只补两件它猜不到的事：
 * 文档标题，以及"令牌怎么传"。没有第二项的话，Swagger UI 右上角的 Authorize 按钮
 * 不会出现，前端只能手工去调登录接口、再复制令牌到别处，也就失去了自助联调的意义。</p>
 *
 * <p>规范与界面地址：{@code /v3/api-docs} 与 {@code /swagger-ui.html}。
 * 这两个路径在 {@code sable.auth.whitelist} 中免认证，生产环境应通过
 * {@code SABLE_OPENAPI_ENABLED=false} 关闭，
 * 避免把全量接口清单暴露给未认证的调用方。</p>
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名，仅用于在规范里引用，会显示为 Authorize 弹窗的标题。 */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI sableOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sablé 数据合规管理系统 API")
                        .version("0.0.1-SNAPSHOT")
                        .description("""
                                由代码生成的实时接口规范 —— 路径、参数、字段以实现为准。

                                认证：先调 POST /auth/login 取得 accessToken，\
                                再点右上角 Authorize 填入该令牌（无需手写 "Bearer " 前缀）。\
                                除白名单接口外，所有请求都需要令牌。

                                注意：本规范描述的是当前实现，尚未覆盖的接口会以空实现出现在列表里；\
                                字段的取舍与设计文档的差异，以本规范为准。"""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Authorization 请求头中的访问令牌")))
                // 全局声明：默认所有接口都要令牌。登录等白名单接口会在规范里也带锁标，
                // 属可接受的失真 —— 反过来漏标受保护接口，危害更大。
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
