package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .DisableMfaCommand;
import com.nursena.payflow.user.application.port.out
    .AccountSecurityAuditPort;
import com.nursena.payflow.user.application.port.out
    .StepUpGrantDigestPort;
import com.nursena.payflow.user.domain.model
    .AccountSecurityAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Import(
    DisableMfaTransactionIntegrationTest
        .FailureInjectionConfiguration.class
)
class DisableMfaTransactionIntegrationTest {

    private static final String STEP_UP_GRANT =
        "disable-mfa-rollback-grant";

    private static final String FAILURE_MESSAGE =
        "disable-mfa audit persistence failed";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DisableMfaService service;

    @Autowired
    private StepUpGrantDigestPort stepUpGrantDigestPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM account_security_audits"
        );
        jdbcTemplate.update("DELETE FROM step_up_grants");
        jdbcTemplate.update(
            "DELETE FROM mfa_recovery_codes"
        );
        jdbcTemplate.update(
            "DELETE FROM mfa_authenticators"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );
        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldRollbackEntireTransactionWhenAuditFails() {
        Instant now = Instant.now();
        UUID userId = insertUser(now);
        UUID familyId = insertActiveFamily(userId, now);
        UUID grantId = insertStepUpGrant(userId, now);

        insertEnabledAuthenticator(userId, now);
        insertRecoveryCode(userId, now);

        assertThatThrownBy(() ->
            service.disable(
                new DisableMfaCommand(
                    userId,
                    STEP_UP_GRANT
                )
            )
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(FAILURE_MESSAGE);

        assertThat(rowCount(
            "mfa_authenticators",
            "user_id",
            userId
        )).isEqualTo(1);

        assertThat(rowCount(
            "mfa_recovery_codes",
            "user_id",
            userId
        )).isEqualTo(1);

        assertThat(stepUpGrantConsumedAt(grantId))
            .isNull();

        assertFamilyUnrevoked(familyId);

        assertThat(rowCount(
            "account_security_audits",
            "subject_user_id",
            userId
        )).isZero();
    }

    private UUID insertUser(Instant now) {
        UUID userId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                email_verified_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            userId + "@example.com",
            "test-password-hash",
            timestamp,
            timestamp,
            timestamp
        );

        return userId;
    }

    private void insertEnabledAuthenticator(
        UUID userId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mfa_authenticators (
                user_id,
                state,
                protected_secret,
                enrollment_expires_at,
                activated_at,
                created_at,
                updated_at
            )
            VALUES (?, 'ENABLED', ?, NULL, ?, ?, ?)
            """,
            userId,
            protectedSecret(),
            Timestamp.from(now.minusSeconds(30)),
            Timestamp.from(now.minusSeconds(60)),
            Timestamp.from(now)
        );
    }

    private void insertRecoveryCode(
        UUID userId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mfa_recovery_codes (
                id,
                user_id,
                code_digest,
                created_at,
                consumed_at
            )
            VALUES (?, ?, ?, ?, NULL)
            """,
            UUID.randomUUID(),
            userId,
            digest((byte) 7),
            Timestamp.from(now.minusSeconds(30))
        );
    }

    private UUID insertActiveFamily(
        UUID userId,
        Instant now
    ) {
        UUID familyId = UUID.randomUUID();

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

    private UUID insertStepUpGrant(
        UUID userId,
        Instant now
    ) {
        UUID grantId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO step_up_grants (
                id,
                subject_id,
                purpose,
                grant_digest,
                issued_at,
                expires_at,
                consumed_at,
                superseded_at
            )
            VALUES (?, ?, 'mfa-disable', ?, ?, ?, NULL, NULL)
            """,
            grantId,
            userId,
            stepUpGrantDigestPort
                .digest(STEP_UP_GRANT)
                .value(),
            Timestamp.from(now.minusSeconds(60)),
            Timestamp.from(now.plusSeconds(600))
        );

        return grantId;
    }

    private Timestamp stepUpGrantConsumedAt(UUID grantId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT consumed_at
            FROM step_up_grants
            WHERE id = ?
            """,
            Timestamp.class,
            grantId
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
                    resultSet.getString(
                        "revocation_reason"
                    )
                ),
            familyId
        );

        assertThat(state).isNotNull();
        assertThat(state.revokedAt()).isNull();
        assertThat(state.reason()).isNull();
    }

    private int rowCount(
        String table,
        String userColumn,
        UUID userId
    ) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + table
                + " WHERE "
                + userColumn
                + " = ?",
            Integer.class,
            userId
        );

        return count == null ? 0 : count;
    }

    private static byte[] protectedSecret() {
        byte[] value = new byte[49];
        Arrays.fill(value, (byte) 11);
        return value;
    }

    private static byte[] digest(byte value) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, value);
        return digest;
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
        FailureInjectingAuditPort
        failureInjectingAuditPort(
            @Qualifier(
                "accountSecurityAuditPersistenceAdapter"
            )
            AccountSecurityAuditPort delegate
        ) {
            return new FailureInjectingAuditPort(delegate);
        }
    }

    static final class FailureInjectingAuditPort
        implements AccountSecurityAuditPort {

        private final AccountSecurityAuditPort delegate;

        FailureInjectingAuditPort(
            AccountSecurityAuditPort delegate
        ) {
            this.delegate = delegate;
        }

        @Override
        public void append(
            AccountSecurityAuditEvent event
        ) {
            delegate.append(event);
            throw new IllegalStateException(
                FAILURE_MESSAGE
            );
        }
    }
}
