package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(
    RevokeAllRefreshSessionsRollbackIntegrationTest
        .FailureInjectionConfiguration.class
)
class RevokeAllRefreshSessionsRollbackIntegrationTest {

    private static final String PASSWORD =
        "StrongPassword123!";

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
    private FailureInjectingFamilyRepository
        familyRepository;

    @BeforeEach
    void setUp() {
        familyRepository.reset();

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
    void shouldRollbackEveryFamilyWhenPersistenceFails()
        throws Exception {

        UUID userId =
            registerUser();

        Instant now =
            Instant.now();

        UUID firstFamilyId =
            insertActiveFamily(
                userId,
                now.minusSeconds(3600),
                now.plusSeconds(86400)
            );

        UUID secondFamilyId =
            insertActiveFamily(
                userId,
                now.minusSeconds(1800),
                now.plusSeconds(172800)
            );

        familyRepository
            .failAfterNextBulkRevocation();

        assertThatThrownBy(() ->
            mockMvc.perform(
                post(
                    "/api/v1/auth/logout-all"
                )
                    .with(
                        jwt().jwt(token ->
                            token.subject(
                                userId.toString()
                            )
                        )
                    )
            )
        )
            .isInstanceOf(
                jakarta.servlet.ServletException.class
            )
            .hasRootCauseInstanceOf(
                IllegalStateException.class
            )
            .hasRootCauseMessage(
                "all-session revocation persistence failed"
            );

        assertFamilyUnrevoked(firstFamilyId);
        assertFamilyUnrevoked(secondFamilyId);
    }

    private UUID registerUser()
        throws Exception {

        String email =
            "all-session-rollback-"
                + UUID.randomUUID()
                + "@example.com";

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RegistrationRequest(
                                email,
                                PASSWORD
                            )
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        UUID userId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                UUID.class,
                email
            );

        assertThat(userId)
            .isNotNull();

        return userId;
    }

    private UUID insertActiveFamily(
        UUID userId,
        Instant createdAt,
        Instant expiresAt
    ) {
        UUID familyId =
            UUID.randomUUID();

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO refresh_token_families (
                    id,
                    user_id,
                    created_at,
                    expires_at,
                    revoked_at,
                    revocation_reason
                )
                VALUES (?, ?, ?, ?, NULL, NULL)
                """,
                familyId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt)
            );

        assertThat(inserted)
            .isEqualTo(1);

        return familyId;
    }

    private void assertFamilyUnrevoked(
        UUID familyId
    ) {
        FamilyState state =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    revoked_at,
                    revocation_reason
                FROM refresh_token_families
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> {
                    Timestamp revokedAt =
                        resultSet.getTimestamp(
                            "revoked_at"
                        );

                    return new FamilyState(
                        revokedAt == null
                            ? null
                            : revokedAt.toInstant(),
                        resultSet.getString(
                            "revocation_reason"
                        )
                    );
                },
                familyId
            );

        assertThat(state)
            .isNotNull();

        assertThat(state.revokedAt())
            .isNull();

        assertThat(state.reason())
            .isNull();
    }

    private record RegistrationRequest(
        String email,
        String password
    ) {
    }

    private record FamilyState(
        Instant revokedAt,
        String reason
    ) {
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class FailureInjectionConfiguration {

        @Bean
        @Primary
        FailureInjectingFamilyRepository
        failureInjectingFamilyRepository(
            @Qualifier(
                "refreshTokenFamilyPersistenceAdapter"
            )
            RefreshTokenFamilyRepositoryPort delegate
        ) {
            return new FailureInjectingFamilyRepository(
                delegate
            );
        }
    }

    static final class FailureInjectingFamilyRepository
        implements RefreshTokenFamilyRepositoryPort {

        private final RefreshTokenFamilyRepositoryPort
            delegate;

        private final AtomicBoolean
            failAfterNextBulkRevocation =
            new AtomicBoolean();

        FailureInjectingFamilyRepository(
            RefreshTokenFamilyRepositoryPort delegate
        ) {
            this.delegate =
                delegate;
        }

        void failAfterNextBulkRevocation() {
            failAfterNextBulkRevocation.set(
                true
            );
        }

        void reset() {
            failAfterNextBulkRevocation.set(
                false
            );
        }

        @Override
        public RefreshTokenFamily save(
            RefreshTokenFamily family
        ) {
            return delegate.save(family);
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
            RefreshTokenFamilyRevocationReason
                reason
        ) {
            int affected =
                delegate.revokeAllActiveByUserId(
                    userId,
                    revokedAt,
                    reason
                );

            if (
                failAfterNextBulkRevocation
                    .compareAndSet(
                        true,
                        false
                    )
            ) {
                throw new IllegalStateException(
                    "all-session revocation "
                        + "persistence failed"
                );
            }

            return affected;
        }
    }
}
