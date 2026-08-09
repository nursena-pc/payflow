package com.nursena.payflow.maildelivery.integration;

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
class MailOutboxMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
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
    void shouldUpgradeV16AndEnforceProtectedMailLifecycle() {
        migrateToVersion("16");
        assertThat(tableExists("mail_outbox_messages")).isFalse();

        migrateToLatestVersion();
        assertThat(currentSchemaVersion()).isEqualTo("20");

        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-05T18:00:00Z");
        insertUser(userId, now);
        insertCredential(credentialId, userId, now);

        jdbcTemplate.update(
            """
            INSERT INTO mail_outbox_messages (
                id, user_id, purpose, recipient, subject,
                protected_body, message_id, status, attempt_count,
                available_at, expires_at, locked_at, locked_until,
                locked_by, created_at, sent_at, last_error
            )
            VALUES (?, ?, 'EMAIL_VERIFICATION', ?, ?, ?, ?,
                    'PENDING', 0, ?, ?, NULL, NULL, NULL, ?, NULL, NULL)
            """,
            credentialId,
            userId,
            "migration@example.com",
            "Verify your PayFlow email",
            new byte[]{1, 2, 3},
            "<account-action-" + credentialId + "@payflow.local>",
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(3600)),
            Timestamp.from(now)
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            UPDATE mail_outbox_messages
            SET status = 'SENT',
                sent_at = ?,
                protected_body = ?
            WHERE id = ?
            """,
            Timestamp.from(now.plusSeconds(1)),
            new byte[]{9},
            credentialId
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.update(
            """
            UPDATE mail_outbox_messages
            SET status = 'SENT',
                sent_at = ?,
                protected_body = NULL
            WHERE id = ?
            """,
            Timestamp.from(now.plusSeconds(1)),
            credentialId
        )).isEqualTo(1);

        assertThat(jdbcTemplate.update(
            "DELETE FROM account_action_credentials WHERE id = ?",
            credentialId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mail_outbox_messages WHERE id = ?",
            Integer.class,
            credentialId
        )).isEqualTo(1);
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

    private static void migrateToVersion(String version) {
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

    private static boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
            )
            """,
            Boolean.class,
            tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    private static void insertUser(UUID userId, Instant now) {
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id, email, password_hash, role, status,
                email_verified_at, created_at, updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', NULL, ?, ?)
            """,
            userId,
            "migration@example.com",
            "$2a$12$hashed-password",
            Timestamp.from(now),
            Timestamp.from(now)
        );
    }

    private static void insertCredential(
        UUID credentialId,
        UUID userId,
        Instant now
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO account_action_credentials (
                id, user_id, purpose, credential_digest,
                issued_at, expires_at, consumed_at, superseded_at
            )
            VALUES (?, ?, 'EMAIL_VERIFICATION', ?, ?, ?, NULL, NULL)
            """,
            credentialId,
            userId,
            new byte[32],
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(3600))
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
