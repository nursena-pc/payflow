package com.nursena.payflow.user.integration;

import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
        "payflow.security.login-rate-limit.enabled=true",
        "payflow.security.login-rate-limit.window=30s",
        "payflow.security.login-rate-limit.identity-limit=2",
        "payflow.security.login-rate-limit.client-limit=3",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=250ms",
        "spring.data.redis.timeout=250ms"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class LoginRateLimitUnavailableHttpIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldFailClosedWith503WhenRedisIsUnavailable()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email": "redis-down@example.com",
                          "password": "WrongPassword123!"
                        }
                        """
                    )
            )
            .andExpect(
                status().isServiceUnavailable()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(503)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "LOGIN_RATE_LIMIT_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Login protection is "
                            + "temporarily unavailable."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/auth/login"
                    )
            );
    }
}
