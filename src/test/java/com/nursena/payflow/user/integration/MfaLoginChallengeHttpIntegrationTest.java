package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "payflow.security.mfa.login-challenge.ttl=5m",
    "payflow.security.mfa.login-challenge.max-attempts=5"
})
@AutoConfigureMockMvc
@Testcontainers
class MfaLoginChallengeHttpIntegrationTest {

    private static final String PASSWORD = "StrongPassword123!";
    private static final byte[] TOTP_SECRET = "01234567890123456789"
        .getBytes(StandardCharsets.US_ASCII);
    private static final String RECOVERY_CODE =
        "AbCdEfGhIjKlMnOpQrStUv";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MfaSecretProtectionPort secretProtection;

    @MockitoBean LoginRateLimitPort loginRateLimit;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM mfa_recovery_codes");
        jdbcTemplate.update("DELETE FROM mfa_login_challenges");
        jdbcTemplate.update("DELETE FROM refresh_token_records");
        jdbcTemplate.update("DELETE FROM refresh_token_families");
        jdbcTemplate.update("DELETE FROM mfa_authenticators");
        jdbcTemplate.update("DELETE FROM users");
        when(loginRateLimit.evaluate(any())).thenReturn(LoginRateLimitDecision.allowed());
    }

    @Test
    void shouldReturnChallengeAndPersistNoCredentialsAfterPasswordStage() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();

        MvcResult login = login(fixture.email());

        assertThat(login.getResponse().getStatus()).isEqualTo(202);
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsByteArray());
        String challenge = body.path("challengeToken").asText();
        assertThat(challenge).hasSize(43);
        assertThat(body.path("authenticationStatus").asText()).isEqualTo("MFA_REQUIRED");
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(count("refresh_token_families")).isZero();
        assertThat(count("refresh_token_records")).isZero();

        byte[] digest = jdbcTemplate.queryForObject(
            "SELECT challenge_digest FROM mfa_login_challenges WHERE user_id = ?",
            byte[].class,
            fixture.userId()
        );
        assertThat(digest).hasSize(32);
        assertThat(Arrays.equals(digest, challenge.getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    @Test
    void shouldIssueCredentialsOnlyAfterValidTotpAndConsumeChallenge() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        String challenge = challengeToken(login(fixture.email()));
        String code = currentTotp(TOTP_SECRET);

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(challenge, code)
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(count("refresh_token_families")).isEqualTo(1);
        assertThat(count("refresh_token_records")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM mfa_login_challenges WHERE user_id = ?",
            String.class,
            fixture.userId()
        )).isEqualTo("CONSUMED");
    }


    @Test
    void shouldConsumeUnusedRecoveryCodeAndIssueCredentials() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        insertRecoveryCode(fixture.userId(), RECOVERY_CODE);
        String challenge = challengeToken(login(fixture.email()));

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(challenge, RECOVERY_CODE)
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
            "SELECT consumed_at IS NOT NULL FROM mfa_recovery_codes WHERE user_id = ?",
            Boolean.class,
            fixture.userId()
        )).isTrue();
        assertThat(count("refresh_token_families")).isEqualTo(1);
        assertThat(count("refresh_token_records")).isEqualTo(1);
    }

    @Test
    void shouldRejectReusedRecoveryCodeThroughGenericFailure() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        insertRecoveryCode(fixture.userId(), RECOVERY_CODE);
        String firstChallenge = challengeToken(login(fixture.email()));

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(firstChallenge, RECOVERY_CODE)
                )))
            .andExpect(status().isOk());

        String secondChallenge = challengeToken(login(fixture.email()));

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(secondChallenge, RECOVERY_CODE)
                )))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempts_remaining FROM mfa_login_challenges WHERE state = 'PENDING' AND user_id = ?",
            Integer.class,
            fixture.userId()
        )).isEqualTo(4);
        assertThat(count("refresh_token_families")).isEqualTo(1);
    }

    @Test
    void shouldPermitExactlyOneConcurrentRecoveryCodeConsumption() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        insertRecoveryCode(fixture.userId(), RECOVERY_CODE);
        String challenge = challengeToken(login(fixture.email()));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(
                () -> confirmStatus(start, challenge, RECOVERY_CODE)
            );
            Future<Integer> second = executor.submit(
                () -> confirmStatus(start, challenge, RECOVERY_CODE)
            );
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(200, 401);
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mfa_recovery_codes WHERE user_id = ? AND consumed_at IS NOT NULL",
            Integer.class,
            fixture.userId()
        )).isEqualTo(1);
        assertThat(count("refresh_token_families")).isEqualTo(1);
        assertThat(count("refresh_token_records")).isEqualTo(1);
    }

    @Test
    void shouldPersistAttemptFailureWithoutIssuingCredentials() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        String challenge = challengeToken(login(fixture.email()));
        String valid = currentTotp(TOTP_SECRET);
        String invalid = valid.substring(0, 5) + (valid.endsWith("0") ? "1" : "0");

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(challenge, invalid)
                )))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempts_remaining FROM mfa_login_challenges WHERE user_id = ?",
            Integer.class,
            fixture.userId()
        )).isEqualTo(4);
        assertThat(count("refresh_token_families")).isZero();
    }

    @Test
    void shouldPermitExactlyOneConcurrentChallengeConsumption() throws Exception {
        UserFixture fixture = insertEnabledMfaUser();
        String challenge = challengeToken(login(fixture.email()));
        String code = currentTotp(TOTP_SECRET);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() -> confirmStatus(start, challenge, code));
            Future<Integer> second = executor.submit(() -> confirmStatus(start, challenge, code));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                .containsExactlyInAnyOrder(200, 401);
        }

        assertThat(count("refresh_token_families")).isEqualTo(1);
        assertThat(count("refresh_token_records")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT state FROM mfa_login_challenges WHERE user_id = ?",
            String.class,
            fixture.userId()
        )).isEqualTo("CONSUMED");
    }

    private int confirmStatus(CountDownLatch start, String challenge, String code) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new ChallengeRequest(challenge, code)
                )))
            .andReturn()
            .getResponse()
            .getStatus();
    }

    private MvcResult login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest(email, PASSWORD)
                )))
            .andReturn();
    }

    private String challengeToken(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
            .path("challengeToken")
            .asText();
    }

    private UserFixture insertEnabledMfaUser() {
        UUID userId = UUID.randomUUID();
        String email = userId + "@example.com";
        Instant now = Instant.now();
        jdbcTemplate.update("""
            INSERT INTO users (
                id, email, password_hash, role, status,
                email_verified_at, created_at, updated_at
            ) VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            email,
            passwordEncoder.encode(PASSWORD),
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );

        ProtectedMfaSecret protectedSecret = secretProtection.protect(
            userId,
            Arrays.copyOf(TOTP_SECRET, TOTP_SECRET.length)
        );
        jdbcTemplate.update("""
            INSERT INTO mfa_authenticators (
                user_id, state, protected_secret,
                enrollment_expires_at, activated_at,
                created_at, updated_at
            ) VALUES (?, 'ENABLED', ?, NULL, ?, ?, ?)
            """,
            userId,
            protectedSecret.value(),
            Timestamp.from(now),
            Timestamp.from(now.minusSeconds(60)),
            Timestamp.from(now)
        );
        return new UserFixture(userId, email);
    }


    private void insertRecoveryCode(UUID userId, String recoveryCode)
        throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(recoveryCode.getBytes(StandardCharsets.US_ASCII));
        jdbcTemplate.update(
            """
            INSERT INTO mfa_recovery_codes (
                id, user_id, code_digest, created_at, consumed_at
            ) VALUES (?, ?, ?, ?, NULL)
            """,
            UUID.randomUUID(),
            userId,
            digest,
            Timestamp.from(Instant.now())
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String currentTotp(byte[] secret) throws Exception {
        long counter = Math.floorDiv(Instant.now().getEpochSecond(), 30L);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
        int offset = digest[digest.length - 1] & 0x0f;
        int binary = ((digest[offset] & 0x7f) << 24)
            | ((digest[offset + 1] & 0xff) << 16)
            | ((digest[offset + 2] & 0xff) << 8)
            | (digest[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }

    private record LoginRequest(String email, String password) {}
    private record ChallengeRequest(String challengeToken, String code) {}
    private record UserFixture(UUID userId, String email) {}
}
