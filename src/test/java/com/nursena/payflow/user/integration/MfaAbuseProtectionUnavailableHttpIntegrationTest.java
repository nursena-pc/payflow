package com.nursena.payflow.user.integration;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "payflow.security.abuse-protection.enabled=true",
    "payflow.security.abuse-protection."
        + "mfa-login-challenge-confirmation.enabled=true",
    "payflow.security.abuse-protection."
        + "mfa-login-challenge-confirmation.dependency-failure-mode=FAIL_CLOSED",
    "payflow.security.abuse-protection."
        + "step-up-grant-issuance.enabled=true",
    "payflow.security.abuse-protection."
        + "step-up-grant-issuance.dependency-failure-mode=FAIL_CLOSED",
    "payflow.security.mfa.login-challenge.max-attempts=5",
    "payflow.security.login-rate-limit.enabled=false",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=1",
    "spring.data.redis.connect-timeout=250ms",
    "spring.data.redis.timeout=250ms"
})
@AutoConfigureMockMvc
@Testcontainers
class MfaAbuseProtectionUnavailableHttpIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

    private static final byte[] TOTP_SECRET =
        "01234567890123456789"
            .getBytes(StandardCharsets.US_ASCII);

    private static final String RECOVERY_CODE =
        "AbCdEfGhIjKlMnOpQrStUv";

    private static final String MFA_CLIENT_ADDRESS =
        "203.0.113.70";

    private static final String STEP_UP_CLIENT_ADDRESS =
        "203.0.113.71";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    }

    @Test
    void shouldFailClosedForBothMfaWorkflowsWithoutSensitiveMutation()
        throws Exception {

        MfaUserFixture fixture = insertEnabledMfaUser(
            jdbcTemplate,
            passwordEncoder,
            secretProtection,
            PASSWORD,
            TOTP_SECRET
        );

        insertRecoveryCode(
            jdbcTemplate,
            fixture.userId(),
            RECOVERY_CODE
        );

        String challenge = challengeToken(
            login(fixture.email())
        );

        assertThat(attemptsRemaining(fixture))
            .isEqualTo(5);
        assertThat(challengeState(fixture))
            .isEqualTo("PENDING");
        assertThat(recoveryCodeConsumed(fixture))
            .isFalse();
        assertThat(grantCount())
            .isZero();
        assertThat(refreshFamilyCount())
            .isZero();
        assertThat(refreshRecordCount())
            .isZero();

        mockMvc.perform(
                post(
                    "/api/v1/auth/mfa/challenges/confirm"
                )
                    .with(request -> {
                        request.setRemoteAddr(
                            MFA_CLIENT_ADDRESS
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
                                RECOVERY_CODE
                            )
                        )
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
                        "MFA_SECURITY_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "MFA security processing is "
                            + "temporarily unavailable."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/auth/mfa/challenges/confirm"
                    )
            );

        assertThat(attemptsRemaining(fixture))
            .isEqualTo(5);
        assertThat(challengeState(fixture))
            .isEqualTo("PENDING");
        assertThat(recoveryCodeConsumed(fixture))
            .isFalse();
        assertThat(refreshFamilyCount())
            .isZero();
        assertThat(refreshRecordCount())
            .isZero();

        mockMvc.perform(
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
                            STEP_UP_CLIENT_ADDRESS
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
                                RECOVERY_CODE
                            )
                        )
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
                        "MFA_SECURITY_UNAVAILABLE"
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "MFA security processing is "
                            + "temporarily unavailable."
                    )
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/api/v1/users/me/step-up/grants"
                    )
            );

        assertThat(attemptsRemaining(fixture))
            .isEqualTo(5);
        assertThat(challengeState(fixture))
            .isEqualTo("PENDING");
        assertThat(recoveryCodeConsumed(fixture))
            .isFalse();
        assertThat(grantCount())
            .isZero();
        assertThat(refreshFamilyCount())
            .isZero();
        assertThat(refreshRecordCount())
            .isZero();
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

        JsonNode body = objectMapper.readTree(
            result.getResponse()
                .getContentAsByteArray()
        );

        String challenge = body
            .path("challengeToken")
            .asText();

        assertThat(challenge)
            .isNotBlank();

        return challenge;
    }

    private int attemptsRemaining(
        MfaUserFixture fixture
    ) {
        Integer value = jdbcTemplate.queryForObject(
            """
            SELECT attempts_remaining
            FROM mfa_login_challenges
            WHERE user_id = ?
            """,
            Integer.class,
            fixture.userId()
        );

        return value == null ? -1 : value;
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
        Boolean consumed = jdbcTemplate.queryForObject(
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

    private int grantCount() {
        return count("step_up_grants");
    }

    private int refreshFamilyCount() {
        return count("refresh_token_families");
    }

    private int refreshRecordCount() {
        return count("refresh_token_records");
    }

    private int count(
        String table
    ) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table,
            Integer.class
        );

        return value == null ? 0 : value;
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

    private record StepUpRequest(
        String purpose,
        String code
    ) {
    }
}
