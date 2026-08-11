package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
class AccountSecurityAuditMigrationIntegrationTest {

    private static final Instant OCCURRED_AT =
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
    void shouldUpgradeV21ToAccountSecurityAuditSchema() {
        migrateTo("21");

        assertThat(tableExists("account_security_audits"))
            .isFalse();

        flyway().migrate();

        assertThat(currentSchemaVersion())
            .isEqualTo("23");
        assertThat(tableExists("account_security_audits"))
            .isTrue();
    }

    @Test
    void shouldStoreOnlySupportedAuditFields() {
        flyway().migrate();

        UUID userId = insertUser();
        UUID auditId = UUID.randomUUID();

        insertAudit(
            auditId,
            userId,
            "MFA_DISABLED",
            OCCURRED_AT
        );

        Integer storedRows = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM account_security_audits
            WHERE id = ?
              AND subject_user_id = ?
              AND action = ?
              AND occurred_at = ?
            """,
            Integer.class,
            auditId,
            userId,
            "MFA_DISABLED",
            Timestamp.from(OCCURRED_AT)
        );

        assertThat(storedRows).isEqualTo(1);

        List<String> columns = jdbcTemplate.queryForList("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'account_security_audits'
            ORDER BY ordinal_position
            """,
            String.class
        );

        assertThat(columns).containsExactly(
            "id",
            "subject_user_id",
            "action",
            "occurred_at"
        );
    }

    @Test
    void shouldEnforceSubjectActionAndAuditRetention() {
        flyway().migrate();

        UUID userId = insertUser();

        assertThatThrownBy(() -> insertAudit(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "MFA_DISABLED",
            OCCURRED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertAudit(
            UUID.randomUUID(),
            userId,
            "UNKNOWN_ACTION",
            OCCURRED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);

        insertAudit(
            UUID.randomUUID(),
            userId,
            "MFA_DISABLED",
            OCCURRED_AT
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "DELETE FROM users WHERE id = ?",
            userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldCreateSubjectTimelineIndex() {
        flyway().migrate();

        String indexDefinition = jdbcTemplate.queryForObject("""
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname =
                  'ix_account_security_audits_subject_occurred_at'
            """,
            String.class
        );

        assertThat(indexDefinition)
            .contains("(subject_user_id, occurred_at DESC)");
    }

    private static UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Timestamp now = Timestamp.from(OCCURRED_AT);

        jdbcTemplate.update("""
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

    private static void insertAudit(
        UUID auditId,
        UUID subjectUserId,
        String action,
        Instant occurredAt
    ) {
        jdbcTemplate.update("""
            INSERT INTO account_security_audits (
                id,
                subject_user_id,
                action,
                occurred_at
            )
            VALUES (?, ?, ?, ?)
            """,
            auditId,
            subjectUserId,
            action,
            Timestamp.from(occurredAt)
        );
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

    private static boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("""
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

    private static String currentSchemaVersion() {
        return jdbcTemplate.queryForObject("""
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
