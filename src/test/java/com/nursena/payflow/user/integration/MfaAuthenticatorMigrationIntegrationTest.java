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
class MfaAuthenticatorMigrationIntegrationTest {

    private static final Instant NOW =
        Instant.parse("2026-08-08T10:00:00Z");

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
        Flyway.configure()
            .cleanDisabled(false)
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .load()
            .clean();
    }

    @Test
    void shouldUpgradeV17ToMfaAuthenticatorSchema() {
        migrateTo("17");
        assertThat(tableExists("mfa_authenticators")).isFalse();
        flyway().migrate();
        assertThat(currentSchemaVersion()).isEqualTo("20");
        assertThat(tableExists("mfa_authenticators")).isTrue();
    }

    @Test
    void shouldAcceptOnePendingAuthenticatorPerUser() {
        flyway().migrate();
        UUID userId = insertUser();
        insertAuthenticator(
            userId,
            "PENDING",
            protectedSecret(),
            NOW.plusSeconds(600),
            null,
            NOW,
            NOW
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mfa_authenticators",
            Integer.class
        )).isEqualTo(1);
        assertThatThrownBy(() -> insertAuthenticator(
            userId,
            "PENDING",
            protectedSecret(),
            NOW.plusSeconds(600),
            null,
            NOW,
            NOW
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectPlainOrInvalidLifecycleStorage() {
        flyway().migrate();
        UUID userId = insertUser();
        assertThatThrownBy(() -> insertAuthenticator(
            userId,
            "PENDING",
            new byte[20],
            NOW.plusSeconds(600),
            null,
            NOW,
            NOW
        )).isInstanceOf(DataIntegrityViolationException.class);

        UUID secondUser = insertUser();
        assertThatThrownBy(() -> insertAuthenticator(
            secondUser,
            "ENABLED",
            protectedSecret(),
            NOW.plusSeconds(600),
            NOW.plusSeconds(60),
            NOW,
            NOW.plusSeconds(60)
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

    private static UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id, email, password_hash, role, status,
                email_verified_at, created_at, updated_at
            )
            VALUES (?, ?, 'hash', 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            userId + "@example.com",
            now,
            now,
            now
        );
        return userId;
    }

    private static void insertAuthenticator(
        UUID userId,
        String state,
        byte[] protectedSecret,
        Instant enrollmentExpiresAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mfa_authenticators (
                user_id, state, protected_secret,
                enrollment_expires_at, activated_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            userId,
            state,
            protectedSecret,
            enrollmentExpiresAt == null ? null : Timestamp.from(enrollmentExpiresAt),
            activatedAt == null ? null : Timestamp.from(activatedAt),
            Timestamp.from(createdAt),
            Timestamp.from(updatedAt)
        );
    }

    private static byte[] protectedSecret() {
        return new byte[49];
    }

    private static boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count == 1;
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
