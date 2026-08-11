package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RefreshTokenFamilyRevocationReasonMigrationIntegrationTest {

    private static final Instant NOW =
        Instant.parse("2026-08-11T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        jdbcTemplate = new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
        );
    }

    @BeforeEach
    void resetDatabase() {
        flyway()
            .clean();
    }

    @Test
    void shouldExpandSupportedRevocationReasonsInV23() {
        migrateTo("22");

        UUID userId = insertUser();
        UUID mfaDisabledFamilyId =
            insertActiveFamily(userId);
        UUID passwordRecoveryFamilyId =
            insertActiveFamily(userId);
        UUID unknownReasonFamilyId =
            insertActiveFamily(userId);

        assertThatThrownBy(() -> revoke(
            mfaDisabledFamilyId,
            "MFA_DISABLED"
        )).isInstanceOf(
            DataIntegrityViolationException.class
        );

        flyway()
            .migrate();

        assertThat(currentSchemaVersion())
            .isEqualTo("23");

        revoke(
            mfaDisabledFamilyId,
            "MFA_DISABLED"
        );
        revoke(
            passwordRecoveryFamilyId,
            "PASSWORD_RECOVERY"
        );

        assertThat(revocationReason(mfaDisabledFamilyId))
            .isEqualTo("MFA_DISABLED");
        assertThat(revocationReason(passwordRecoveryFamilyId))
            .isEqualTo("PASSWORD_RECOVERY");

        assertThatThrownBy(() -> revoke(
            unknownReasonFamilyId,
            "UNKNOWN_REASON"
        )).isInstanceOf(
            DataIntegrityViolationException.class
        );
    }

    private static UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(NOW);

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
            VALUES (?, ?, 'test-password-hash',
                    'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            userId + "@example.com",
            timestamp,
            timestamp,
            timestamp
        );

        return userId;
    }

    private static UUID insertActiveFamily(UUID userId) {
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
            Timestamp.from(NOW),
            Timestamp.from(NOW.plusSeconds(3_600))
        );

        return familyId;
    }

    private static void revoke(
        UUID familyId,
        String reason
    ) {
        jdbcTemplate.update(
            """
            UPDATE refresh_token_families
            SET revoked_at = ?,
                revocation_reason = ?
            WHERE id = ?
            """,
            Timestamp.from(NOW.plusSeconds(60)),
            reason,
            familyId
        );
    }

    private static String revocationReason(
        UUID familyId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT revocation_reason
            FROM refresh_token_families
            WHERE id = ?
            """,
            String.class,
            familyId
        );
    }

    private static void migrateTo(String version) {
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .target(version)
            .load()
            .migrate();
    }

    private static Flyway flyway() {
        return Flyway.configure()
            .cleanDisabled(false)
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .load();
    }

    private static String currentSchemaVersion() {
        return jdbcTemplate.queryForObject(
            """
            SELECT version
            FROM flyway_schema_history
            WHERE success = TRUE
              AND version IS NOT NULL
            ORDER BY installed_rank DESC
            LIMIT 1
            """,
            String.class
        );
    }
}
