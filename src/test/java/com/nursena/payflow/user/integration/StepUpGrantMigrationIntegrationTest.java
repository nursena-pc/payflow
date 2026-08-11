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
class StepUpGrantMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-10T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ));
    }

    @BeforeEach
    void resetDatabase() {
        Flyway.configure().cleanDisabled(false)
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load().clean();
    }

    @Test
    void shouldUpgradeV20ToStepUpGrantSchema() {
        migrateTo("20");
        assertThat(tableExists("step_up_grants")).isFalse();
        flyway().migrate();
        assertThat(currentSchemaVersion()).isEqualTo("21");
        assertThat(tableExists("step_up_grants")).isTrue();
    }

    @Test
    void shouldStoreDigestOnlySubjectPurposeAndLifetimeMetadata() {
        flyway().migrate();
        UUID user = insertUser();
        insertGrant(user, "mfa-disable", digest((byte) 1), NOW.plusSeconds(300), null, null);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT octet_length(grant_digest) FROM step_up_grants", Integer.class
        )).isEqualTo(32);
        Integer plaintext = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema='public' AND table_name='step_up_grants'
              AND column_name IN ('grant','grant_token','plaintext')
            """, Integer.class);
        assertThat(plaintext).isZero();
    }

    @Test
    void shouldRejectUnknownPurposeWrongDigestAndInvalidLifetime() {
        flyway().migrate();
        UUID user = insertUser();
        assertThatThrownBy(() -> insertGrant(
            user, "free-form", digest((byte) 2), NOW.plusSeconds(300), null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGrant(
            user, "mfa-disable", new byte[31], NOW.plusSeconds(300), null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGrant(
            user, "mfa-disable", digest((byte) 3), NOW, null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateDigestAndImpossibleTerminalState() {
        flyway().migrate();
        UUID user = insertUser();
        byte[] digest = digest((byte) 4);
        insertGrant(user, "mfa-disable", digest, NOW.plusSeconds(300), null, null);
        assertThatThrownBy(() -> insertGrant(
            user, "recovery-code-rotation", digest, NOW.plusSeconds(300), null, null
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGrant(
            user, "recovery-code-rotation", digest((byte) 5), NOW.plusSeconds(300),
            NOW.plusSeconds(10), NOW.plusSeconds(20)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertGrant(UUID user, String purpose, byte[] digest, Instant expires,
        Instant consumed, Instant superseded) {
        jdbcTemplate.update("""
            INSERT INTO step_up_grants (
                id, subject_id, purpose, grant_digest, issued_at, expires_at,
                consumed_at, superseded_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), user, purpose, digest, Timestamp.from(NOW), Timestamp.from(expires),
            consumed == null ? null : Timestamp.from(consumed),
            superseded == null ? null : Timestamp.from(superseded)
        );
    }

    private static UUID insertUser() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbcTemplate.update("""
            INSERT INTO users (id,email,password_hash,role,status,email_verified_at,created_at,updated_at)
            VALUES (?,?,'hash','USER','ACTIVE',?,?,?)
            """, id, id + "@example.com", now, now, now);
        return id;
    }

    private static byte[] digest(byte value) { byte[] d = new byte[32]; Arrays.fill(d, value); return d; }
    private static Flyway flyway() { return Flyway.configure().cleanDisabled(false).dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load(); }
    private static void migrateTo(String version) { Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).target(version).load().migrate(); }
    private static boolean tableExists(String name) { Boolean v=jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=?)",Boolean.class,name); return Boolean.TRUE.equals(v); }
    private static String currentSchemaVersion() { return jdbcTemplate.queryForObject("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",String.class); }
}
