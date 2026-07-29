package com.nursena.payflow.configuration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RevokeAllRefreshSessionsOpenApiIntegrationTest {

    private static final String LOGOUT_ALL_PATH =
        "/api/v1/auth/logout-all";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode operation;

    @BeforeEach
    void loadOperation() throws Exception {
        MvcResult result =
            mockMvc.perform(
                    get("/v3/api-docs")
                )
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    content()
                        .contentTypeCompatibleWith(
                            MediaType.APPLICATION_JSON
                        )
                )
                .andReturn();

        JsonNode openApi =
            objectMapper.readTree(
                result
                    .getResponse()
                    .getContentAsByteArray()
            );

        operation =
            openApi
                .path("paths")
                .path(LOGOUT_ALL_PATH)
                .path("post");
    }

    @Test
    void shouldExposeAuthenticatedNoContentContract() {
        assertThat(operation.isObject())
            .isTrue();

        assertThat(
            operation
                .path("operationId")
                .asText()
        )
            .isEqualTo(
                "revokeAllRefreshSessions"
            );

        assertThat(
            operation
                .path("security")
                .get(0)
                .has(
                    OpenApiConfiguration
                        .BEARER_AUTH_SCHEME
                )
        )
            .isTrue();

        assertThat(
            operation.has("requestBody")
        )
            .isFalse();

        Set<String> responseCodes =
            new LinkedHashSet<>();

        operation
            .path("responses")
            .fieldNames()
            .forEachRemaining(
                responseCodes::add
            );

        assertThat(responseCodes)
            .containsExactlyInAnyOrder(
                "204",
                "401",
                "500"
            );
    }

    @Test
    void shouldExposeNoCredentialOrIdentitySchema() {
        assertThat(
            operation
                .path("responses")
                .path("204")
                .has("content")
        )
            .isFalse();

        assertThat(
            operation
                .path("responses")
                .path("401")
                .has("content")
        )
            .isFalse();

        assertThat(
            operation.toString()
        )
            .doesNotContain(
                "userId",
                "refreshToken",
                "tokenDigest",
                "sessionCount"
            );
    }
}
