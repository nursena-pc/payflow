package com.nursena.payflow.user.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
        "payflow.security.refresh-session.refresh-token-ttl=7d",
        "payflow.security.refresh-session.family-ttl=30d"
    }
)
@AutoConfigureMockMvc
@Testcontainers
@Import(
    RotateRefreshCredentialsRollbackIntegrationTest
        .FailureInjectionConfiguration.class
)
class RotateRefreshCredentialsRollbackIntegrationTest {

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
    private RefreshTokenDigestPort
        refreshTokenDigest;

    @Autowired
    private FailureInjectingRecordRepository
        recordRepository;

    @Autowired
    private FailureInjectingFamilyRepository
        familyRepository;

    @Autowired
    private FailureInjectingAccessTokenGeneration
        accessTokenGeneration;

    @BeforeEach
    void setUp() {
        recordRepository.reset();
        familyRepository.reset();
        accessTokenGeneration.reset();

        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );

        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );

        jdbcTemplate.update(
            "DELETE FROM users"
        );
    }

    @Test
    void shouldRollbackWhenSuccessorPersistenceFails()
        throws Exception {

        String refreshToken =
            issueInitialRefreshToken(
                "successor-failure"
            );

        recordRepository.failOnSaveAttempt(
            1,
            "successor persistence failed"
        );

        assertThatThrownBy(() ->
            refresh(refreshToken)
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "successor persistence failed"
            );

        assertInitialSessionUnchanged(
            refreshToken
        );
    }

    @Test
    void shouldRollbackWhenPredecessorPersistenceFails()
        throws Exception {

        String refreshToken =
            issueInitialRefreshToken(
                "predecessor-failure"
            );

        recordRepository.failOnSaveAttempt(
            2,
            "predecessor persistence failed"
        );

        assertThatThrownBy(() ->
            refresh(refreshToken)
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "predecessor persistence failed"
            );

        assertInitialSessionUnchanged(
            refreshToken
        );
    }

    @Test
    void shouldRollbackWhenAccessTokenGenerationFails()
        throws Exception {

        String refreshToken =
            issueInitialRefreshToken(
                "access-failure"
            );

        accessTokenGeneration
            .failNextGeneration();

        assertThatThrownBy(() ->
            refresh(refreshToken)
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "access-token generation failed"
            );

        assertInitialSessionUnchanged(
            refreshToken
        );
    }

    @Test
    void shouldRollbackWhenReuseRevocationPersistenceFails()
        throws Exception {

        String initialRefreshToken =
            issueInitialRefreshToken(
                "reuse-revocation-failure"
            );

        org.springframework.test.web.servlet
        .MvcResult firstRotation =
            refresh(initialRefreshToken)
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstResponse =
            objectMapper.readTree(
                firstRotation
                    .getResponse()
                    .getContentAsByteArray()
            );

        String successorToken =
            firstResponse
                .path("refreshToken")
                .asText();

        int accessGenerationCountBeforeReuse =
            accessTokenGeneration
                .generationCount();

        familyRepository.failAfterNextSave(
            "family revocation persistence failed"
        );

        assertThatThrownBy(() ->
            refresh(initialRefreshToken)
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "family revocation persistence failed"
            );

        assertFamilyRevocationRolledBack(
            initialRefreshToken
        );

        assertThat(
            accessTokenGeneration
                .generationCount()
        )
            .isEqualTo(
                accessGenerationCountBeforeReuse
            );

        refresh(successorToken)
            .andExpect(status().isOk());
    }

    private String issueInitialRefreshToken(
        String prefix
    ) throws Exception {

        Credentials credentials =
            new Credentials(
                prefix
                    + "-"
                    + UUID.randomUUID()
                    + "@example.com",
                "StrongPassword123!"
            );

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            credentials
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        org.springframework.test.web.servlet
        .MvcResult loginResult =
            mockMvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            objectMapper.writeValueAsString(
                                credentials
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        JsonNode response =
            objectMapper.readTree(
                loginResult
                    .getResponse()
                    .getContentAsByteArray()
            );

        return response
            .path("refreshToken")
            .asText();
    }

    private org.springframework.test.web.servlet
    .ResultActions refresh(
        String refreshToken
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    objectMapper.writeValueAsString(
                        new RefreshRequest(
                            refreshToken
                        )
                    )
                )
        );
    }

    private void assertInitialSessionUnchanged(
        String refreshToken
    ) {
        byte[] digest =
            refreshTokenDigest
                .digest(refreshToken)
                .value();

        Integer familyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                """,
                Integer.class
            );

        Integer recordCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                """,
                Integer.class
            );

        Integer activeOriginalCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                WHERE token_digest = ?
                  AND consumed_at IS NULL
                  AND successor_id IS NULL
                """,
                Integer.class,
                digest
            );

        byte[] persistedDigest =
            jdbcTemplate.queryForObject(
                """
                SELECT token_digest
                FROM refresh_token_records
                """,
                byte[].class
            );

        assertThat(familyCount)
            .isEqualTo(1);

        assertThat(recordCount)
            .isEqualTo(1);

        assertThat(activeOriginalCount)
            .isEqualTo(1);

        assertThat(persistedDigest)
            .containsExactly(digest);

        assertThat(
            Arrays.equals(
                persistedDigest,
                refreshToken.getBytes(UTF_8)
            )
        )
            .isFalse();
    }

    private void assertFamilyRevocationRolledBack(
        String initialRefreshToken
    ) {
        byte[] digest =
            refreshTokenDigest
                .digest(initialRefreshToken)
                .value();

        FamilyState familyState =
            jdbcTemplate.queryForObject(
                """
                SELECT family.revoked_at,
                       family.revocation_reason
                FROM refresh_token_families family
                JOIN refresh_token_records record
                  ON record.family_id = family.id
                WHERE record.token_digest = ?
                """,
                (resultSet, rowNumber) ->
                    new FamilyState(
                        resultSet
                            .getTimestamp(
                                "revoked_at"
                            ),
                        resultSet.getString(
                            "revocation_reason"
                        )
                    ),
                digest
            );

        Integer recordCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                """,
                Integer.class
            );

        Integer consumedCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                WHERE consumed_at IS NOT NULL
                  AND successor_id IS NOT NULL
                """,
                Integer.class
            );

        Integer unconsumedCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                WHERE consumed_at IS NULL
                  AND successor_id IS NULL
                """,
                Integer.class
            );

        assertThat(familyState)
            .isNotNull();

        assertThat(familyState.revokedAt())
            .isNull();

        assertThat(familyState.reason())
            .isNull();

        assertThat(recordCount)
            .isEqualTo(2);

        assertThat(consumedCount)
            .isEqualTo(1);

        assertThat(unconsumedCount)
            .isEqualTo(1);
    }

    private record FamilyState(
        java.sql.Timestamp revokedAt,
        String reason
    ) {
    }

    private record Credentials(
        String email,
        String password
    ) {
    }

    private record RefreshRequest(
        String refreshToken
    ) {
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingRecordRepository
        failureInjectingRotationRecordRepository(
            @Qualifier(
                "refreshTokenRecordPersistenceAdapter"
            )
            RefreshTokenRecordRepositoryPort delegate
        ) {
            return new FailureInjectingRecordRepository(
                delegate
            );
        }

        @Bean
        @Primary
        FailureInjectingFamilyRepository
        failureInjectingRotationFamilyRepository(
            @Qualifier(
                "refreshTokenFamilyPersistenceAdapter"
            )
            RefreshTokenFamilyRepositoryPort delegate
        ) {
            return new FailureInjectingFamilyRepository(
                delegate
            );
        }

        @Bean
        @Primary
        FailureInjectingAccessTokenGeneration
        failureInjectingRotationAccessTokenGeneration(
            @Qualifier(
                "jwtAccessTokenGenerationAdapter"
            )
            AccessTokenGenerationPort delegate
        ) {
            return new FailureInjectingAccessTokenGeneration(
                delegate
            );
        }
    }

    static final class FailureInjectingRecordRepository
        implements RefreshTokenRecordRepositoryPort {

        private final RefreshTokenRecordRepositoryPort
            delegate;

        private final AtomicInteger saveAttempts =
            new AtomicInteger();

        private volatile int failureAttempt =
            -1;

        private volatile String failureMessage =
            "";

        FailureInjectingRecordRepository(
            RefreshTokenRecordRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void failOnSaveAttempt(
            int attempt,
            String message
        ) {
            saveAttempts.set(0);
            failureAttempt = attempt;
            failureMessage = message;
        }

        void reset() {
            saveAttempts.set(0);
            failureAttempt = -1;
            failureMessage = "";
        }

        @Override
        public RefreshTokenRecord save(
            RefreshTokenRecord record
        ) {
            int attempt =
                saveAttempts.incrementAndGet();

            if (attempt == failureAttempt) {
                throw new IllegalStateException(
                    failureMessage
                );
            }

            return delegate.save(record);
        }

        @Override
        public Optional<RefreshTokenRecord>
        findByDigestForUpdate(
            RefreshTokenDigest digest
        ) {
            return delegate
                .findByDigestForUpdate(
                    digest
                );
        }

        @Override
        public Optional<RefreshTokenRecord>
        findById(
            RefreshTokenRecordId recordId
        ) {
            return delegate.findById(
                recordId
            );
        }
    }

    static final class FailureInjectingFamilyRepository
        implements RefreshTokenFamilyRepositoryPort {

        private final RefreshTokenFamilyRepositoryPort
            delegate;

        private final AtomicBoolean
            failAfterNextSave =
            new AtomicBoolean();

        private volatile String failureMessage =
            "";

        FailureInjectingFamilyRepository(
            RefreshTokenFamilyRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void failAfterNextSave(
            String message
        ) {
            failureMessage = message;
            failAfterNextSave.set(true);
        }

        void reset() {
            failAfterNextSave.set(false);
            failureMessage = "";
        }

        @Override
        public RefreshTokenFamily save(
            RefreshTokenFamily family
        ) {
            RefreshTokenFamily saved =
                delegate.save(family);

            if (
                failAfterNextSave.compareAndSet(
                    true,
                    false
                )
            ) {
                throw new IllegalStateException(
                    failureMessage
                );
            }

            return saved;
        }

        @Override
        public Optional<RefreshTokenFamily>
        findByIdForUpdate(
            RefreshTokenFamilyId familyId
        ) {
            return delegate.findByIdForUpdate(
                familyId
            );
        }

        @Override
        public int revokeAllActiveByUserId(
            UUID userId,
            Instant revokedAt,
            RefreshTokenFamilyRevocationReason reason
        ) {
            return delegate.revokeAllActiveByUserId(
                userId,
                revokedAt,
                reason
            );
        }
    }

    static final class FailureInjectingAccessTokenGeneration
        implements AccessTokenGenerationPort {

        private final AccessTokenGenerationPort
            delegate;

        private final AtomicBoolean
            failNextGeneration =
            new AtomicBoolean();

        private final AtomicInteger
            generationCount =
            new AtomicInteger();

        FailureInjectingAccessTokenGeneration(
            AccessTokenGenerationPort delegate
        ) {
            this.delegate = delegate;
        }

        void failNextGeneration() {
            failNextGeneration.set(true);
        }

        void reset() {
            failNextGeneration.set(false);
            generationCount.set(0);
        }

        int generationCount() {
            return generationCount.get();
        }

        @Override
        public GeneratedAccessToken generate(
            User user
        ) {
            generationCount.incrementAndGet();

            if (
                failNextGeneration.compareAndSet(
                    true,
                    false
                )
            ) {
                throw new IllegalStateException(
                    "access-token generation failed"
                );
            }

            return delegate.generate(user);
        }
    }
}
