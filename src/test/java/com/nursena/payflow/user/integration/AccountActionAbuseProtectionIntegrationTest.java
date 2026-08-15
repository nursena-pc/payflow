package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "payflow.security.abuse-protection.enabled=true",
    "payflow.security.abuse-protection.email-verification-request.identity-limit=3",
    "payflow.security.abuse-protection.email-verification-request.client-limit=3",
    "payflow.security.abuse-protection.password-recovery-request.identity-limit=3",
    "payflow.security.abuse-protection.password-recovery-request.client-limit=3",
    "payflow.security.login-rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class AccountActionAbuseProtectionIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

    private static final int ATTEMPTS = 8;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine")
        ).withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        jdbcTemplate.update("DELETE FROM users");

        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection.serverCommands().flushAll();
                return null;
            }
        );
    }

    @Test
    void shouldBoundConcurrentEmailVerificationSideEffects()
        throws Exception {

        String email = "limited.verification@example.com";
        register(email);
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );

        List<Integer> statuses = concurrentRequests(
            "/api/v1/auth/email-verification/requests",
            email
        );

        assertThat(statuses).hasSize(ATTEMPTS)
            .allMatch(status -> status == 202);
        assertThat(credentialCount("EMAIL_VERIFICATION"))
            .isEqualTo(3);
    }

    @Test
    void shouldBoundConcurrentPasswordRecoverySideEffects()
        throws Exception {

        String email = "limited.recovery@example.com";
        register(email);
        jdbcTemplate.update(
            "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP "
                + "WHERE email = ?",
            email
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );

        List<Integer> statuses = concurrentRequests(
            "/api/v1/auth/password-recovery/requests",
            email
        );

        assertThat(statuses).hasSize(ATTEMPTS)
            .allMatch(status -> status == 202);
        assertThat(credentialCount("PASSWORD_RECOVERY"))
            .isEqualTo(3);
    }

    @Test
    void shouldKeepEligibilityAndQuotaOutcomesPubliclyGeneric()
        throws Exception {

        String eligible = "eligible@example.com";
        String verified = "verified@example.com";
        String closed = "closed@example.com";
        String unknown = "unknown@example.com";

        register(eligible);
        register(verified);
        register(closed);
        jdbcTemplate.update(
            "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP "
                + "WHERE email = ?",
            verified
        );
        jdbcTemplate.update(
            "UPDATE users SET status = 'CLOSED' WHERE email = ?",
            closed
        );
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection.serverCommands().flushAll();
                return null;
            }
        );

        List<String> identities = List.of(
            eligible,
            verified,
            closed,
            unknown
        );

        for (int index = 0; index < identities.size(); index++) {
            String body = request(
                "/api/v1/auth/email-verification/requests",
                identities.get(index),
                "203.0.113." + (20 + index)
            );
            assertThat(body).isEmpty();
        }

        assertThat(credentialCount("EMAIL_VERIFICATION"))
            .isEqualTo(1);
    }

    private List<Integer> concurrentRequests(
        String path,
        String email
    ) throws Exception {
        ExecutorService executor =
            Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < ATTEMPTS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                            "concurrent start timed out"
                        );
                    }

                    return emailStatus(
                        path,
                        email,
                        "203.0.113.10"
                    );
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                .isTrue();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void register(String email) throws Exception {
        int status = rawStatus(
            "/api/v1/auth/register",
            """
            {
              "email": "%s",
              "password": "%s"
            }
            """.formatted(email, PASSWORD),
            "198.51.100.10"
        );
        assertThat(status).isEqualTo(201);
    }

    private int emailStatus(
        String path,
        String email,
        String remoteAddress
    ) throws Exception {
        return rawStatus(
            path,
            """
            {
              "email": "%s"
            }
            """.formatted(email),
            remoteAddress
        );
    }

    private int rawStatus(
        String path,
        String json,
        String remoteAddress
    ) throws Exception {
        return mockMvc.perform(
                requestBuilder(path, json, remoteAddress)
            )
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private String request(
        String path,
        String email,
        String remoteAddress
    ) throws Exception {
        return mockMvc.perform(
                requestBuilder(
                    path,
                    """
                    {
                      "email": "%s"
                    }
                    """.formatted(email),
                    remoteAddress
                )
            )
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private static MockHttpServletRequestBuilder requestBuilder(
        String path,
        String json,
        String remoteAddress
    ) {
        return post(path)
            .with(request -> {
                request.setRemoteAddr(remoteAddress);
                return request;
            })
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
    }

    private int credentialCount(String purpose) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account_action_credentials "
                + "WHERE purpose = ?",
            Integer.class,
            purpose
        );
        return count == null ? 0 : count;
    }
}