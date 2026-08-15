package com.nursena.payflow.user.integration;

import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.differentTotp;
import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.insertEnabledMfaUser;
import static com.nursena.payflow.user.support
    .MfaSecurityIntegrationTestSupport.insertRecoveryCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request
    .SecurityMockMvcRequestPostProcessors.jwt;
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
        + "step-up-grant-issuance.window=5m",
    "payflow.security.abuse-protection."
        + "step-up-grant-issuance.identity-limit=2",
    "payflow.security.abuse-protection."
        + "step-up-grant-issuance.client-limit=3",
    "payflow.security.login-rate-limit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers
class StepUpAbuseProtectionHttpIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

    private static final byte[] TOTP_SECRET =
        "01234567890123456789"
            .getBytes(StandardCharsets.US_ASCII);

    private static final String RECOVERY_CODE =
        "AbCdEfGhIjKlMnOpQrStUv";

    private static final String CLIENT_ADDRESS =
        "203.0.113.60";

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
            "DELETE FROM step_up_grants"
        );
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
    void shouldBlockSubjectQuotaBeforeRecoveryCodeOrGrantMutation()
        throws Exception {

        MfaUserFixture fixture = user();
        insertRecoveryCode(
            jdbcTemplate,
            fixture.userId(),
            RECOVERY_CODE
        );

        String invalidCode =
            differentTotp(TOTP_SECRET);

        stepUp(
            fixture,
            invalidCode,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_VERIFICATION_FAILED")
            );

        stepUp(
            fixture,
            invalidCode,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_VERIFICATION_FAILED")
            );

        assertThat(grantCount()).isZero();
        assertThat(
            recoveryCodeConsumed(fixture)
        ).isFalse();

        stepUp(
            fixture,
            RECOVERY_CODE,
            CLIENT_ADDRESS,
            null,
            null
        )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("STEP_UP_INVALID")
            );

        assertThat(grantCount()).isZero();
        assertThat(
            recoveryCodeConsumed(fixture)
        ).isFalse();

        assertRedisKeysDoNotExpose(
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

        for (int index = 0; index < 4; index++) {
            fixtures.add(user());
        }

        String invalidCode =
            differentTotp(TOTP_SECRET);

        for (int index = 0; index < 3; index++) {
            String spoofed =
                "198.51.100." + (30 + index);

            stepUp(
                fixtures.get(index),
                invalidCode,
                CLIENT_ADDRESS,
                "for=" + spoofed,
                spoofed
            )
                .andExpect(status().isUnauthorized())
                .andExpect(
                    jsonPath("$.code")
                        .value(
                            "MFA_VERIFICATION_FAILED"
                        )
                );
        }

        MfaUserFixture blockedFixture =
            fixtures.get(3);

        insertRecoveryCode(
            jdbcTemplate,
            blockedFixture.userId(),
            RECOVERY_CODE
        );

        stepUp(
            blockedFixture,
            RECOVERY_CODE,
            CLIENT_ADDRESS,
            "for=192.0.2.240",
            "192.0.2.240"
        )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("STEP_UP_INVALID")
            );

        assertThat(grantCount()).isZero();
        assertThat(
            recoveryCodeConsumed(blockedFixture)
        ).isFalse();

        assertRedisKeysDoNotExpose(
            CLIENT_ADDRESS,
            "198.51.100.30",
            "198.51.100.31",
            "198.51.100.32",
            "192.0.2.240",
            blockedFixture.userId().toString(),
            RECOVERY_CODE,
            invalidCode
        );
    }

    @Test
    void shouldBoundConcurrentGrantAndRecoveryCodeConsumptionByClientQuota()
        throws Exception {

        List<MfaUserFixture> fixtures =
            new ArrayList<>();

        List<String> recoveryCodes =
            new ArrayList<>();

        for (
            int index = 0;
            index < CONCURRENT_REQUESTS;
            index++
        ) {
            MfaUserFixture fixture = user();
            String recoveryCode =
                "ConcurrentCode0"
                    + index
                    + "AbCdEf";

            fixtures.add(fixture);
            recoveryCodes.add(recoveryCode);

            insertRecoveryCode(
                jdbcTemplate,
                fixture.userId(),
                recoveryCode
            );
        }

        List<Integer> statuses =
            concurrentStepUps(
                fixtures,
                recoveryCodes
            );

        assertThat(statuses)
            .hasSize(CONCURRENT_REQUESTS)
            .allMatch(
                value ->
                    value == 200
                        || value == 403
            );

        long successCount =
            statuses.stream()
                .filter(value -> value == 200)
                .count();

        assertThat(successCount)
            .isEqualTo(3);

        assertThat(grantCount())
            .isEqualTo(3);

        assertThat(consumedRecoveryCodeCount())
            .isEqualTo(3);

        List<String> sensitiveValues =
            new ArrayList<>();

        sensitiveValues.add(CLIENT_ADDRESS);
        sensitiveValues.addAll(recoveryCodes);

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

    private List<Integer> concurrentStepUps(
        List<MfaUserFixture> fixtures,
        List<String> recoveryCodes
    ) throws Exception {

        if (fixtures.size() != recoveryCodes.size()) {
            throw new IllegalArgumentException(
                "fixtures and recoveryCodes must align"
            );
        }

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
            for (int index = 0; index < fixtures.size(); index++) {
                MfaUserFixture fixture = fixtures.get(index);
                String recoveryCode = recoveryCodes.get(index);

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
                                "Concurrent step-up start "
                                    + "timed out."
                            );
                        }

                        return stepUp(
                            fixture,
                            recoveryCode,
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

    private ResultActions stepUp(
        MfaUserFixture fixture,
        String code,
        String remoteAddress,
        String forwarded,
        String xForwardedFor
    ) throws Exception {

        MockHttpServletRequestBuilder builder =
            post(
                "/api/v1/users/me/step-up/grants"
            )
                .with(
                    jwt().jwt(token ->
                        token.subject(
                            fixture.userId().toString()
                        )
                            .claim("role", "USER")
                    )
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
                        new StepUpRequest(
                            "mfa-disable",
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

    private int consumedRecoveryCodeCount() {
        Integer value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM mfa_recovery_codes
                WHERE consumed_at IS NOT NULL
                """,
                Integer.class
            );

        return value == null
            ? 0
            : value;
    }

    private int grantCount() {
        Integer value =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM step_up_grants",
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

    private record StepUpRequest(
        String purpose,
        String code
    ) {
    }
}
