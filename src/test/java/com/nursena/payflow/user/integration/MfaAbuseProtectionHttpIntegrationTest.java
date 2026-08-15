package com.nursena.payflow.user.integration;

import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.currentTotp;
import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.differentTotp;
import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.insertEnabledMfaUser;
import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.insertRecoveryCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out
    .MfaSecretProtectionPort;
import com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.MfaUserFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request
    .MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
    "payflow.security.abuse-protection.enabled=true",
    "payflow.security.abuse-protection."
        + "mfa-login-challenge-confirmation.window=5m",
    "payflow.security.abuse-protection."
        + "mfa-login-challenge-confirmation.identity-limit=2",
    "payflow.security.abuse-protection."
        + "mfa-login-challenge-confirmation.client-limit=3",
    "payflow.security.mfa.login-challenge.max-attempts=5",
    "payflow.security.login-rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class MfaAbuseProtectionHttpIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

    private static final byte[] TOTP_SECRET =
        "01234567890123456789"
            .getBytes(StandardCharsets.US_ASCII);

    private static final String RECOVERY_CODE =
        "AbCdEfGhIjKlMnOpQrStUv";

    private static final String CLIENT_ADDRESS =
        "203.0.113.50";

    private static final int CONCURRENT_REQUESTS = 8;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Container
    @ServiceConnection(name = "redis")
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(
            DockerImageName.parse(
                "redis:8-alpine"
            )
        ).withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MfaSecretProtectionPort secretProtection;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.update(
            "DELETE FROM mfa_recovery_codes"
        );
        jdbcTemplate.update(
            "DELETE FROM mfa_login_challenges"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );
        jdbcTemplate.update(
            "DELETE FROM mfa_authenticators"
        );
        jdbcTemplate.update(
            "DELETE FROM users"
        );

        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection.serverCommands().flushAll();
                return null;
            }
        );
    }

    @Test
    void shouldBlockRepeatedChallengeBeforeSensitiveMutation()
        throws Exception {

        MfaUserFixture fixture = user();
        insertRecoveryCode(
            jdbcTemplate,
            fixture.userId(),
            RECOVERY_CODE
        );

        String challenge =
            challengeToken(login(fixture.email()));

        String invalidCode =
            differentTotp(TOTP_SECRET);

        confirm(
            challenge,
            invalidCode,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_CHALLENGE_INVALID")
            );

        confirm(
            challenge,
            invalidCode,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_CHALLENGE_INVALID")
            );

        assertThat(
            attemptsRemaining(fixture)
        ).isEqualTo(3);

        confirm(
            challenge,
            RECOVERY_CODE,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_CHALLENGE_INVALID")
            );

        assertThat(
            attemptsRemaining(fixture)
        ).isEqualTo(3);

        assertThat(
            recoveryCodeConsumed(fixture)
        ).isFalse();

        assertThat(
            challengeState(fixture)
        ).isEqualTo("PENDING");

        assertThat(
            count("refresh_token_families")
        ).isZero();

        assertThat(
            count("refresh_token_records")
        ).isZero();

        assertRedisKeysDoNotExpose(
            challenge,
            fixture.userId().toString(),
            CLIENT_ADDRESS,
            RECOVERY_CODE,
            invalidCode
        );
    }

    @Test
    void shouldIgnoreSpoofedForwardingHeadersForClientQuota()
        throws Exception {

        List<MfaUserFixture> fixtures =
            new ArrayList<>();

        List<String> challenges =
            new ArrayList<>();

        for (int index = 0; index < 4; index++) {
            MfaUserFixture fixture = user();
            fixtures.add(fixture);
            challenges.add(
                challengeToken(
                    login(fixture.email())
                )
            );
        }

        String invalidCode =
            differentTotp(TOTP_SECRET);

        for (int index = 0; index < 3; index++) {
            String spoofed =
                "198.51.100." + (20 + index);

            confirm(
                challenges.get(index),
                invalidCode,
                CLIENT_ADDRESS,
                "for=" + spoofed,
                spoofed
            )
                .andExpect(status().isUnauthorized())
                .andExpect(
                    jsonPath("$.code")
                        .value(
                            "MFA_CHALLENGE_INVALID"
                        )
                );

            assertThat(
                attemptsRemaining(
                    fixtures.get(index)
                )
            ).isEqualTo(4);
        }

        MfaUserFixture blockedFixture =
            fixtures.get(3);

        insertRecoveryCode(
            jdbcTemplate,
            blockedFixture.userId(),
            RECOVERY_CODE
        );

        confirm(
            challenges.get(3),
            RECOVERY_CODE,
            CLIENT_ADDRESS,
            "for=192.0.2.250",
            "192.0.2.250"
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_CHALLENGE_INVALID")
            );

        assertThat(
            attemptsRemaining(blockedFixture)
        ).isEqualTo(5);

        assertThat(
            recoveryCodeConsumed(blockedFixture)
        ).isFalse();

        assertThat(
            count("refresh_token_families")
        ).isZero();

        assertRedisKeysDoNotExpose(
            CLIENT_ADDRESS,
            "198.51.100.20",
            "198.51.100.21",
            "198.51.100.22",
            "192.0.2.250",
            RECOVERY_CODE
        );
    }

    @Test
    void shouldBoundConcurrentCredentialIssuanceByClientQuota()
        throws Exception {

        List<MfaUserFixture> fixtures =
            new ArrayList<>();

        List<String> challenges =
            new ArrayList<>();

        for (
            int index = 0;
            index < CONCURRENT_REQUESTS;
            index++
        ) {
            MfaUserFixture fixture = user();
            fixtures.add(fixture);
            challenges.add(
                challengeToken(
                    login(fixture.email())
                )
            );
        }

        String validCode =
            currentTotp(TOTP_SECRET);

        List<Integer> statuses =
            concurrentConfirmations(
                challenges,
                validCode
            );

        assertThat(statuses)
            .hasSize(CONCURRENT_REQUESTS)
            .allMatch(
                value ->
                    value == 200
                        || value == 401
            );

        long successCount =
            statuses.stream()
                .filter(value -> value == 200)
                .count();

        assertThat(successCount)
            .isEqualTo(3);

        assertThat(
            count("refresh_token_families")
        ).isEqualTo(3);

        assertThat(
            count("refresh_token_records")
        ).isEqualTo(3);

        Integer consumedChallenges =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM mfa_login_challenges
                WHERE state = 'CONSUMED'
                """,
                Integer.class
            );

        assertThat(consumedChallenges)
            .isEqualTo(3);

        List<String> sensitiveValues =
            new ArrayList<>();

        sensitiveValues.add(CLIENT_ADDRESS);
        sensitiveValues.add(validCode);
        sensitiveValues.addAll(challenges);

        for (MfaUserFixture fixture : fixtures) {
            sensitiveValues.add(
                fixture.userId().toString()
            );
        }

        assertRedisKeysDoNotExpose(
            sensitiveValues.toArray(
                String[]::new
            )
        );
    }

    private List<Integer> concurrentConfirmations(
        List<String> challenges,
        String code
    ) throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(
                CONCURRENT_REQUESTS
            );

        CountDownLatch ready =
            new CountDownLatch(
                CONCURRENT_REQUESTS
            );

        CountDownLatch start =
            new CountDownLatch(1);

        List<Future<Integer>> futures =
            new ArrayList<>();

        try {
            for (String challenge : challenges) {
                futures.add(
                    executor.submit(() -> {
                        ready.countDown();

                        if (
                            !start.await(
                                10,
                                TimeUnit.SECONDS
                            )
                        ) {
                            throw new IllegalStateException(
                                "Concurrent MFA "
                                    + "confirmation start "
                                    + "timed out."
                            );
                        }

                        return confirm(
                            challenge,
                            code,
                            CLIENT_ADDRESS,
                            null,
                            null
                        )
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    })
                );
            }

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();

            start.countDown();

            List<Integer> statuses =
                new ArrayList<>();

            for (Future<Integer> future : futures) {
                statuses.add(
                    future.get(
                        30,
                        TimeUnit.SECONDS
                    )
                );
            }

            return statuses;
        } finally {
            start.countDown();
            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();
        }
    }

    private MfaUserFixture user() {
        return insertEnabledMfaUser(
            jdbcTemplate,
            passwordEncoder,
            secretProtection,
            PASSWORD,
            TOTP_SECRET
        );
    }

    private MvcResult login(
        String email
    ) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                    .with(request -> {
                        request.setRemoteAddr(
                            "198.51.100.10"
                        );
                        return request;
                    })
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(
                                email,
                                PASSWORD
                            )
                        )
                    )
            )
            .andReturn();
    }

    private String challengeToken(
        MvcResult result
    ) throws Exception {
        assertThat(
            result.getResponse().getStatus()
        ).isEqualTo(202);

        JsonNode body =
            objectMapper.readTree(
                result.getResponse()
                    .getContentAsByteArray()
            );

        String challenge =
            body.path("challengeToken")
                .asText();

        assertThat(challenge)
            .isNotBlank();

        return challenge;
    }

    private ResultActions confirm(
        String challenge,
        String code,
        String remoteAddress,
        String forwarded,
        String xForwardedFor
    ) throws Exception {

        MockHttpServletRequestBuilder builder =
            post(
                "/api/v1/auth/mfa/challenges/confirm"
            )
                .with(request -> {
                    request.setRemoteAddr(
                        remoteAddress
                    );
                    return request;
                })
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper.writeValueAsString(
                        new ChallengeRequest(
                            challenge,
                            code
                        )
                    )
                );

        if (forwarded != null) {
            builder.header(
                "Forwarded",
                forwarded
            );
        }

        if (xForwardedFor != null) {
            builder.header(
                "X-Forwarded-For",
                xForwardedFor
            );
        }

        return mockMvc.perform(builder);
    }

    private int attemptsRemaining(
        MfaUserFixture fixture
    ) {
        Integer attempts =
            jdbcTemplate.queryForObject(
                """
                SELECT attempts_remaining
                FROM mfa_login_challenges
                WHERE user_id = ?
                """,
                Integer.class,
                fixture.userId()
            );

        return attempts == null
            ? -1
            : attempts;
    }

    private String challengeState(
        MfaUserFixture fixture
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT state
            FROM mfa_login_challenges
            WHERE user_id = ?
            """,
            String.class,
            fixture.userId()
        );
    }

    private boolean recoveryCodeConsumed(
        MfaUserFixture fixture
    ) {
        Boolean consumed =
            jdbcTemplate.queryForObject(
                """
                SELECT consumed_at IS NOT NULL
                FROM mfa_recovery_codes
                WHERE user_id = ?
                """,
                Boolean.class,
                fixture.userId()
            );

        return Boolean.TRUE.equals(consumed);
    }

    private int count(
        String table
    ) {
        Integer value =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class
            );

        return value == null
            ? 0
            : value;
    }

    private void assertRedisKeysDoNotExpose(
        String... sensitiveValues
    ) {
        Set<String> keys =
            redisTemplate.keys(
                "payflow:security:abuse:v1:*"
            );

        assertThat(keys)
            .isNotNull()
            .isNotEmpty();

        for (String sensitiveValue : sensitiveValues) {
            assertThat(sensitiveValue)
                .isNotNull()
                .isNotBlank();

            assertThat(keys)
                .allSatisfy(
                    key ->
                        assertThat(key)
                            .doesNotContain(
                                sensitiveValue
                            )
                );
        }
    }

    private record LoginRequest(
        String email,
        String password
    ) {
    }

    private record ChallengeRequest(
        String challengeToken,
        String code
    ) {
    }
}