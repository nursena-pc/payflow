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
class MfaLoginChallengeMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");

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
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load()
            .clean();
    }

    @Test
    void shouldUpgradeV18ToChallengeSchema() {
        migrateTo("18");
        assertThat(tableExists("mfa_login_challenges")).isFalse();
        flyway().migrate();
        assertThat(currentSchemaVersion()).isEqualTo("23");
        assertThat(tableExists("mfa_login_challenges")).isTrue();
    }

    @Test
    void shouldStoreOnlyFixedLengthChallengeDigest() {
        flyway().migrate();
        UUID userId = insertUser();
        insertChallenge(userId, new byte[32], "PENDING", 5, null);
        byte[] stored = jdbcTemplate.queryForObject(
            "SELECT challenge_digest FROM mfa_login_challenges",
            byte[].class
        );
        assertThat(stored).hasSize(32);
    }

    @Test
    void shouldRejectNonSha256DigestLength() {
        flyway().migrate();
        UUID userId = insertUser();
        assertThatThrownBy(() -> insertChallenge(userId, new byte[31], "PENDING", 5, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowAtMostOnePendingChallengePerUser() {
        flyway().migrate();
        UUID userId = insertUser();
        insertChallenge(userId, digest((byte) 1), "PENDING", 5, null);
        assertThatThrownBy(() -> insertChallenge(userId, digest((byte) 2), "PENDING", 5, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRequireResolvedTimestampForTerminalState() {
        flyway().migrate();
        UUID userId = insertUser();
        assertThatThrownBy(() -> insertChallenge(userId, digest((byte) 3), "CONSUMED", 5, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRequireZeroAttemptsForExhaustedState() {
        flyway().migrate();
        UUID userId = insertUser();
        assertThatThrownBy(() -> insertChallenge(
            userId,
            digest((byte) 4),
            "EXHAUSTED",
            1,
            NOW.plusSeconds(10)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertChallenge(
        UUID userId,
        byte[] digest,
        String state,
        int attempts,
        Instant resolvedAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO mfa_login_challenges (
                id, user_id, challenge_digest, issued_at, expires_at,
                attempts_remaining, state, resolved_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            digest,
            Timestamp.from(NOW),
            Timestamp.from(NOW.plusSeconds(300)),
            attempts,
            state,
            resolvedAt == null ? null : Timestamp.from(resolvedAt)
        );
    }

    private static UUID insertUser() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbcTemplate.update("""
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
        java.util.Arrays.fill(digest, value);
        return digest;
    }

    private static Flyway flyway() {
        return Flyway.configure()
            .cleanDisabled(false)
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();
    }

    private static void migrateTo(String version) {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .target(version)
            .load()
            .migrate();
    }

    private static boolean tableExists(String name) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = ?
            """, Integer.class, name);
        return count != null && count == 1;
    }

    private static String currentSchemaVersion() {
        return jdbcTemplate.queryForObject("""
            SELECT version FROM flyway_schema_history
            WHERE success = TRUE AND version IS NOT NULL
            ORDER BY installed_rank DESC LIMIT 1
            """, String.class);
    }
}
