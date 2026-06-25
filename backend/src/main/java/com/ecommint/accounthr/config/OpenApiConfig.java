package com.ecommint.accounthr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * springdoc-openapi yapılandırması (E1-07).
 *
 * <p>API başlığı/sürümü/açıklaması ve JWT bearer güvenlik şeması ({@code bearerAuth})
 * tanımlanır; bu sayede Swagger UI'da "Authorize" butonu çıkar ve korumalı uçlar
 * token ile denenebilir. Swagger UI: {@code /swagger-ui/index.html},
 * OpenAPI JSON: {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI accountHrOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("account-hr API")
                        .version("v1")
                        .description("E-Commint ön muhasebe & fatura yönetim platformu REST API. "
                                + "Tüm uçlar /api/v1 altındadır; hata yanıtları standart ErrorResponse "
                                + "şeklini kullanır; korumalı uçlar JWT bearer token gerektirir."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token. 'Bearer ' öneki Swagger UI tarafından eklenir.")));
    }
}
