package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model
    .RefreshTokenFamilyRevocationReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet
    .AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
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
    PasswordRecoveryRollbackIntegrationTest
        .FailureInjectionConfiguration.class
)
class PasswordRecoveryRollbackIntegrationTest {

    private static final String EMAIL =
        "recovery.rollback@example.com";

    private static final String OLD_PASSWORD =
        "StrongPassword123!";

    private static final String NEW_PASSWORD =
        "ReplacementPassword123!";

    private static final String EMAIL_CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final String RECOVERY_CREDENTIAL =
        "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingFamilyRepository
        familyRepository;

    @Autowired
    private DeterministicCredentialGenerator
        credentialGenerator;

    @BeforeEach
    void setUp() {
        familyRepository.reset();
        credentialGenerator.reset(
            EMAIL_CREDENTIAL,
            RECOVERY_CREDENTIAL
        );

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
    }

    @Test
    void shouldRollbackCredentialPasswordAndFamilyRevocation()
        throws Exception {

        registerAndVerify();

        UUID userId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE email = ?",
            UUID.class,
            EMAIL
        );
        String originalPasswordHash = storedPasswordHash();
        UUID familyId = insertActiveFamily(userId);

        requestRecovery();
        familyRepository.failAfterNextBulkRevocation();

        assertThatThrownBy(() ->
            mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s",
                          "newPassword": "%s"
                        }
                        """.formatted(
                            RECOVERY_CREDENTIAL,
                            NEW_PASSWORD
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
                "password-recovery session revocation failed"
            );

        assertThat(storedPasswordHash())
            .isEqualTo(originalPasswordHash);
        assertThat(recoveryConsumedAt()).isNull();
        assertFamilyUnrevoked(familyId);
    }

    private void registerAndVerify() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(EMAIL, OLD_PASSWORD)
                    )
            )
            .andExpect(status().isCreated());

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s"
                        }
                        """.formatted(EMAIL_CREDENTIAL)
                    )
            )
            .andExpect(status().isNoContent());
    }

    private void requestRecovery() throws Exception {
        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/requests"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s"
                        }
                        """.formatted(EMAIL)
                    )
            )
            .andExpect(status().isAccepted());
    }

    private UUID insertActiveFamily(UUID userId) {
        UUID familyId = UUID.randomUUID();
        Instant now = Instant.now();

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
            Timestamp.from(now.minusSeconds(60)),
            Timestamp.from(now.plusSeconds(3_600))
        );

        return familyId;
    }

    private String storedPasswordHash() {
        return jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE email = ?",
            String.class,
            EMAIL
        );
    }

    private Timestamp recoveryConsumedAt() {
        return jdbcTemplate.queryForObject(
            """
            SELECT consumed_at
            FROM account_action_credentials
            WHERE purpose = 'PASSWORD_RECOVERY'
            """,
            Timestamp.class
        );
    }

    private void assertFamilyUnrevoked(UUID familyId) {
        FamilyState state = jdbcTemplate.queryForObject(
            """
            SELECT revoked_at, revocation_reason
            FROM refresh_token_families
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                new FamilyState(
                    resultSet.getTimestamp("revoked_at"),
                    resultSet.getString("revocation_reason")
                ),
            familyId
        );

        assertThat(state).isNotNull();
        assertThat(state.revokedAt()).isNull();
        assertThat(state.reason()).isNull();
    }

    private record FamilyState(
        Timestamp revokedAt,
        String reason
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
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

        @Bean
        @Primary
        DeterministicCredentialGenerator
        deterministicCredentialGenerator() {
            return new DeterministicCredentialGenerator();
        }
    }

    static final class FailureInjectingFamilyRepository
        implements RefreshTokenFamilyRepositoryPort {

        private final RefreshTokenFamilyRepositoryPort
            delegate;
        private final AtomicBoolean failAfterRevocation =
            new AtomicBoolean();

        FailureInjectingFamilyRepository(
            RefreshTokenFamilyRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void failAfterNextBulkRevocation() {
            failAfterRevocation.set(true);
        }

        void reset() {
            failAfterRevocation.set(false);
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
            return delegate.findByIdForUpdate(familyId);
        }

        @Override
        public int revokeAllActiveByUserId(
            UUID userId,
            Instant revokedAt,
            RefreshTokenFamilyRevocationReason reason
        ) {
            int affected = delegate.revokeAllActiveByUserId(
                userId,
                revokedAt,
                reason
            );

            if (failAfterRevocation.compareAndSet(
                true,
                false
            )) {
                throw new IllegalStateException(
                    "password-recovery session revocation failed"
                );
            }

            return affected;
        }
    }

    static final class DeterministicCredentialGenerator
        implements AccountActionCredentialGenerationPort {

        private final Queue<String> values =
            new ConcurrentLinkedQueue<>();

        void reset(String... credentials) {
            values.clear();
            values.addAll(Arrays.asList(credentials));
        }

        @Override
        public GeneratedAccountActionCredential generate() {
            String value = values.poll();

            if (value == null) {
                throw new IllegalStateException(
                    "No deterministic credential is available."
                );
            }

            return new GeneratedAccountActionCredential(value);
        }
    }
}
