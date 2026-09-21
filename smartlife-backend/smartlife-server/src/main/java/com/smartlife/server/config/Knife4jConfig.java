package com.smartlife.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j 4.x / OpenAPI 3）。
 * 4.x 是 springdoc 系，与 3.0.3 的 springfox 系不是同一套 API，也不再需要 ant_path_matcher 补丁。
 */
@Configuration
public class Knife4jConfig {

    private static final String SECURITY_SCHEME_NAME = "Authorization";

    @Bean
    public OpenAPI smartLifeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智慧生活 SmartLife API")
                        .description("""
                                本地生活服务平台，用户端 / 商家端 / 管理端三端共用一套接口。

                                **鉴权方式**：先调 `/auth/login` 拿到 token，
                                再点击右上角「Authorize」填入（只填 token 本身，不用加 Bearer 前缀）。
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("SmartLife")))
                // 声明为 HTTP bearer，让 Knife4j 的调试面板自动带上 authorization 头
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name(SECURITY_SCHEME_NAME)))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
