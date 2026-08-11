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
class PasswordRecoveryMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        DriverManagerDataSource dataSource =
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            );

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void resetDatabase() {
        flyway().clean();
    }

    @Test
    void shouldUpgradeV15AndPermitPasswordRecoveryRevocation() {
        migrateToVersion("15");

        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        Instant now = Instant.parse(
            "2026-08-05T12:00:00Z"
        );

        insertUser(userId, now);
        insertFamily(familyId, userId, now);

        assertThatThrownBy(() -> revoke(
            familyId,
            now.plusSeconds(60),
            "PASSWORD_RECOVERY"
        )).isInstanceOf(DataIntegrityViolationException.class);

        migrateToLatestVersion();

        assertThat(currentSchemaVersion()).isEqualTo("21");

        assertThat(revoke(
            familyId,
            now.plusSeconds(60),
            "PASSWORD_RECOVERY"
        )).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            UPDATE refresh_token_families
            SET revocation_reason = 'UNSUPPORTED'
            WHERE id = ?
            """,
            familyId
        )).isInstanceOf(DataIntegrityViolationException.class);
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

    private static void migrateToVersion(
        String version
    ) {
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

    private static void migrateToLatestVersion() {
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .load()
            .migrate();
    }

    private static void insertUser(
        UUID userId,
        Instant now
    ) {
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
            "migration-recovery@example.com",
            "$2a$12$hashed-password",
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private static void insertFamily(
        UUID familyId,
        UUID userId,
        Instant now
    ) {
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
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(3_600))
        );
    }

    private static int revoke(
        UUID familyId,
        Instant revokedAt,
        String reason
    ) {
        return jdbcTemplate.update(
            """
            UPDATE refresh_token_families
            SET revoked_at = ?,
                revocation_reason = ?
            WHERE id = ?
            """,
            Timestamp.from(revokedAt),
            reason,
            familyId
        );
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
