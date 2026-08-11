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
class MfaRecoveryCodeMigrationIntegrationTest {

    private static final Instant NOW =
        Instant.parse("2026-08-09T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(),
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        ));
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
    void shouldUpgradeV19ToDigestOnlyRecoveryCodeSchema() {
        migrateTo("19");
        assertThat(tableExists("mfa_recovery_codes")).isFalse();

        flyway().migrate();

        assertThat(currentSchemaVersion()).isEqualTo("23");
        assertThat(tableExists("mfa_recovery_codes")).isTrue();
    }

    @Test
    void shouldStoreFixedLengthDigestWithoutPlaintextColumn() {
        flyway().migrate();
        UUID userId = insertUser();
        byte[] digest = digest((byte) 1);
        insertRecoveryCode(userId, digest, null);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT code_digest FROM mfa_recovery_codes",
            byte[].class
        )).containsExactly(digest);

        Integer plaintextColumns = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'mfa_recovery_codes'
              AND column_name IN ('code', 'recovery_code', 'plaintext')
            """,
            Integer.class
        );
        assertThat(plaintextColumns).isZero();
    }

    @Test
    void shouldRejectWrongDigestLengthAndDuplicateDigest() {
        flyway().migrate();
        UUID firstUser = insertUser();
        UUID secondUser = insertUser();
        byte[] digest = digest((byte) 2);

        assertThatThrownBy(() -> insertRecoveryCode(
            firstUser,
            new byte[31],
            null
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertRecoveryCode(firstUser, digest, null);

        assertThatThrownBy(() -> insertRecoveryCode(
            secondUser,
            digest,
            null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPermitTenIndependentCodesForOneUser() {
        flyway().migrate();
        UUID userId = insertUser();

        for (int index = 0; index < 10; index++) {
            insertRecoveryCode(
                userId,
                digest((byte) (index + 1)),
                null
            );
        }

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mfa_recovery_codes WHERE user_id = ?",
            Integer.class,
            userId
        )).isEqualTo(10);
    }

    @Test
    void shouldRejectConsumptionBeforeCreation() {
        flyway().migrate();
        UUID userId = insertUser();

        assertThatThrownBy(() -> insertRecoveryCode(
            userId,
            digest((byte) 12),
            NOW.minusSeconds(1)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertRecoveryCode(
        UUID userId,
        byte[] digest,
        Instant consumedAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mfa_recovery_codes (
                id, user_id, code_digest, created_at, consumed_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            digest,
            Timestamp.from(NOW),
            consumedAt == null ? null : Timestamp.from(consumedAt)
        );
    }

    private static UUID insertUser() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id, email, password_hash, role, status,
                email_verified_at, created_at, updated_at
            ) VALUES (?, ?, 'hash', 'USER', 'ACTIVE', ?, ?, ?)
            """,
            id,
            id + "@example.com",
            now,
            now,
            now
        );
        return id;
    }

    private static byte[] digest(byte value) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, value);
        return digest;
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

    private static boolean tableExists(String name) {
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
            name
        );
        return Boolean.TRUE.equals(exists);
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
