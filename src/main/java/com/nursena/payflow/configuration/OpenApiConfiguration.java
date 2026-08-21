package com.nursena.payflow.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BEARER_AUTH_SCHEME =
        "bearerAuth";

    private static final String API_TITLE =
        "PayFlow API";

    private static final String API_VERSION =
        "0.16.0-SNAPSHOT";

    private static final String API_DESCRIPTION =
        """
        REST API for the PayFlow simulated digital wallet \
        and payment transaction backend.

        PayFlow does not process real money.
        """;

    @Bean
    OpenAPI payflowOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title(API_TITLE)
                    .version(API_VERSION)
                    .description(API_DESCRIPTION)
            )
            .components(
                new Components()
                    .addSecuritySchemes(
                        BEARER_AUTH_SCHEME,
                        bearerJwtSecurityScheme()
                    )
            );
    }

    private static SecurityScheme
    bearerJwtSecurityScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description(
                "RSA-signed JWT access token returned "
                    + "by the login endpoint."
            );
    }
}
