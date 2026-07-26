package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RefreshSessionMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void configureJdbcTemplate() {
        DriverManagerDataSource dataSource =
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            );

        jdbcTemplate =
            new JdbcTemplate(dataSource);
    }

    @Test
    void shouldUpgradeV13ToV14AndPreserveExistingUserData() {
        migrateToVersion("13");

        assertThat(currentSchemaVersion())
            .isEqualTo("13");

        assertThat(migrationApplied("14"))
            .isFalse();

        assertThat(
            tableExists(
                "refresh_token_families"
            )
        )
            .isFalse();

        assertThat(
            tableExists(
                "refresh_token_records"
            )
        )
            .isFalse();

        UUID existingUserId =
            UUID.randomUUID();

        insertUser(existingUserId);

        assertThat(userCount(existingUserId))
            .isEqualTo(1);

        migrateToLatestVersion();

        assertThat(currentSchemaVersion())
            .isEqualTo("14");

        assertThat(migrationApplied("13"))
            .isTrue();

        assertThat(migrationApplied("14"))
            .isTrue();

        assertThat(
            tableExists(
                "refresh_token_families"
            )
        )
            .isTrue();

        assertThat(
            tableExists(
                "refresh_token_records"
            )
        )
            .isTrue();

        assertThat(userCount(existingUserId))
            .isEqualTo(1);

        assertThat(failedMigrationCount())
            .isZero();
    }

    private static void migrateToVersion(
        String targetVersion
    ) {
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
        Flyway.configure()
            .dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
            )
            .load()
            .migrate();
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

    private static boolean migrationApplied(
        String version
    ) {
        Boolean applied =
            jdbcTemplate.queryForObject(
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

    private static boolean tableExists(
        String tableName
    ) {
        Boolean exists =
            jdbcTemplate.queryForObject(
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

    private static int failedMigrationCount() {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = FALSE
                """,
                Integer.class
            );

        return count == null
            ? 0
            : count;
    }

    private static void insertUser(
        UUID userId
    ) {
        Instant now =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Timestamp timestamp =
            Timestamp.from(now);

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
            timestamp,
            timestamp
        );
    }

    private static int userCount(
        UUID userId
    ) {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                """,
                Integer.class,
                userId
            );

        return count == null
            ? 0
            : count;
    }
}
