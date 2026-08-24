package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

    private OpenAPI openApi;

    @BeforeEach
    void setUp() {
        openApi =
            new OpenApiConfiguration()
                .payflowOpenApi();
    }

    @Test
    void shouldExposePayflowApiMetadata() {
        assertThat(openApi.getInfo())
            .isNotNull();

        assertThat(openApi.getInfo().getTitle())
            .isEqualTo("PayFlow API");

        assertThat(openApi.getInfo().getVersion())
            .isEqualTo("0.16.0");

        assertThat(openApi.getInfo().getDescription())
            .contains(
                "simulated digital wallet"
            )
            .contains(
                "does not process real money"
            );
    }

    @Test
    void shouldExposeBearerJwtSecurityScheme() {
        assertThat(openApi.getComponents())
            .isNotNull();

        assertThat(
            openApi
                .getComponents()
                .getSecuritySchemes()
        )
            .containsKey(
                OpenApiConfiguration
                    .BEARER_AUTH_SCHEME
            );

        SecurityScheme securityScheme =
            openApi
                .getComponents()
                .getSecuritySchemes()
                .get(
                    OpenApiConfiguration
                        .BEARER_AUTH_SCHEME
                );

        assertThat(securityScheme.getType())
            .isEqualTo(
                SecurityScheme.Type.HTTP
            );

        assertThat(securityScheme.getScheme())
            .isEqualTo("bearer");

        assertThat(securityScheme.getBearerFormat())
            .isEqualTo("JWT");

        assertThat(securityScheme.getDescription())
            .contains(
                "login endpoint"
            );
    }

    @Test
    void shouldNotRequireAuthenticationGlobally() {
        assertThat(openApi.getSecurity())
            .isNullOrEmpty();
    }
}
