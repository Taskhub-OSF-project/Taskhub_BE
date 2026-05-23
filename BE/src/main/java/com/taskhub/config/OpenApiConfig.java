package com.taskhub.config;

import com.taskhub.controller.AuthController;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class OpenApiConfig {

    public static final String JWT_SCHEME = "JWT";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Paste token from POST /api/auth/login (field data.token). Example: eyJhbGci...");

        return new OpenAPI()
                .info(new Info()
                        .title("TaskHub API")
                        .version("1.0")
                        .description("""
                                1. Call **POST /api/auth/register** or **/login**
                                2. Copy `data.token` from response
                                3. Click **Authorize** (top right) and paste the token
                                4. Call wallet/task APIs
                                """))
                .components(new Components().addSecuritySchemes(JWT_SCHEME, bearer));
    }

    /** Gắn ổ khóa JWT cho mọi API trừ /api/auth — Swagger UI hiện nút Authorize. */
    @Bean
    public OperationCustomizer jwtOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (!isAuthEndpoint(handlerMethod)) {
                operation.addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME));
            }
            return operation;
        };
    }

    private static boolean isAuthEndpoint(HandlerMethod handlerMethod) {
        return AuthController.class.isAssignableFrom(handlerMethod.getBeanType());
    }
}
