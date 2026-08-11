package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
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
class AccountActionCredentialMigrationIntegrationTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-04T12:00:00Z");

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
    void shouldUpgradeV14ToLatestAndVerifyExistingUsers() {
        migrateToVersion("14");

        UUID existingUserId = UUID.randomUUID();

        insertUser(existingUserId);

        assertThat(columnExists(
            "users",
            "email_verified_at"
        )).isFalse();
        assertThat(tableExists(
            "account_action_credentials"
        )).isFalse();

        migrateToLatestVersion();

        assertThat(currentSchemaVersion()).isEqualTo("23");
        assertThat(migrationApplied("15")).isTrue();
        assertThat(migrationApplied("16")).isTrue();
        assertThat(tableExists(
            "account_action_credentials"
        )).isTrue();

        Timestamp emailVerifiedAt =
            jdbcTemplate.queryForObject(
                """
                SELECT email_verified_at
                FROM users
                WHERE id = ?
                """,
                Timestamp.class,
                existingUserId
            );

        assertThat(emailVerifiedAt)
            .isNotNull();
        assertThat(emailVerifiedAt.toInstant())
            .isAfterOrEqualTo(CREATED_AT);
        assertThat(failedMigrationCount()).isZero();
    }

    @Test
    void shouldInstallConstrainedCredentialStorageOnCleanDatabase() {
        migrateToLatestVersion();

        UUID userId = UUID.randomUUID();

        insertUser(userId);

        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT email_verified_at IS NULL
            FROM users
            WHERE id = ?
            """,
            Boolean.class,
            userId
        )).isTrue();

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            UPDATE users
            SET email_verified_at = ?
            WHERE id = ?
            """,
            Timestamp.from(CREATED_AT.minusSeconds(1)),
            userId
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertCredential(
            UUID.randomUUID(),
            userId,
            "EMAIL_VERIFICATION",
            digest((byte) 1),
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            null,
            null
        );

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "EMAIL_VERIFICATION",
            digest((byte) 2),
            CREATED_AT.plusSeconds(60),
            CREATED_AT.plusSeconds(3_660),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "PASSWORD_RECOVERY",
            new byte[31],
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "UNSUPPORTED",
            digest((byte) 3),
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "PASSWORD_RECOVERY",
            digest((byte) 4),
            CREATED_AT,
            CREATED_AT,
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "PASSWORD_RECOVERY",
            digest((byte) 5),
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            CREATED_AT.plusSeconds(60),
            CREATED_AT.plusSeconds(120)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertCredential(
            UUID.randomUUID(),
            userId,
            "PASSWORD_RECOVERY",
            digest((byte) 1),
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            null,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertCredential(
            UUID.randomUUID(),
            userId,
            "PASSWORD_RECOVERY",
            digest((byte) 6),
            CREATED_AT,
            CREATED_AT.plusSeconds(3_600),
            null,
            null
        );

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM account_action_credentials",
            Integer.class
        )).isEqualTo(2);

        assertThat(indexExists(
            "uq_account_action_credentials_unresolved"
        )).isTrue();
        assertThat(failedMigrationCount()).isZero();
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

    private static void migrateToVersion(String targetVersion) {
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .target(targetVersion)
            .load()
            .migrate();
    }

    private static void migrateToLatestVersion() {
        flyway().migrate();
    }

    private static void insertUser(UUID userId) {
        Timestamp createdAt = Timestamp.from(CREATED_AT);

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?)
            """,
            userId,
            userId + "@example.com",
            "test-password-hash",
            createdAt,
            createdAt
        );
    }

    private static void insertCredential(
        UUID id,
        UUID userId,
        String purpose,
        byte[] digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO account_action_credentials (
                id,
                user_id,
                purpose,
                credential_digest,
                issued_at,
                expires_at,
                consumed_at,
                superseded_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            userId,
            purpose,
            digest,
            Timestamp.from(issuedAt),
            Timestamp.from(expiresAt),
            consumedAt == null
                ? null
                : Timestamp.from(consumedAt),
            supersededAt == null
                ? null
                : Timestamp.from(supersededAt)
        );
    }

    private static byte[] digest(byte value) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, value);
        return digest;
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

    private static boolean migrationApplied(String version) {
        Boolean applied = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM flyway_schema_history
                WHERE version = ?
                  AND success = TRUE
            )
            """,
            Boolean.class,
            version
        );

        return Boolean.TRUE.equals(applied);
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

    private static boolean columnExists(
        String tableName,
        String columnName
    ) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            )
            """,
            Boolean.class,
            tableName,
            columnName
        );

        return Boolean.TRUE.equals(exists);
    }

    private static boolean indexExists(String indexName) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = ?
            )
            """,
            Boolean.class,
            indexName
        );

        return Boolean.TRUE.equals(exists);
    }

    private static int failedMigrationCount() {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE success = FALSE
            """,
            Integer.class
        );

        return count == null ? 0 : count;
    }
}
