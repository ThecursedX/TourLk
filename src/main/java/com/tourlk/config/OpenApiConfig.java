package com.tourlk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI setup. UI is served at /swagger-ui.html (see
 * springdoc.swagger-ui.path in application.yml). Adds a bearer-token
 * scheme so JWTs can be supplied via the "Authorize" button.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI tourlkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TourLK API")
                        .description("Sri Lanka Tourism Booking Platform - API contract shared across all modules")
                        .version("v0.1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

}
