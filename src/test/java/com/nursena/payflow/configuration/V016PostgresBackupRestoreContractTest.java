package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V016PostgresBackupRestoreContractTest {

    private static final Path SCRIPT =
        Path.of(
            "scripts",
            "operations",
            "postgres-backup-restore-rehearsal.ps1"
        );

    private static final Path GUIDE =
        Path.of(
            "docs",
            "operations",
            "postgresql-backup-restore.md"
        );

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path GITIGNORE =
        Path.of(".gitignore");

    private static final Path COMPOSE =
        Path.of("compose.yml");

    private static final Path API_BASELINE =
        Path.of("docs", "api-v1-compatibility.md");

    @Test
    void shouldCommitSafePostgres17BackupAndIsolatedRestoreProcedure()
        throws IOException {

        String script = Files.readString(SCRIPT);

        assertThat(Files.readString(COMPOSE))
            .contains("image: postgres:17-alpine");

        assertThat(script)
            .contains(
                "postgres:17-alpine",
                "Get-ExpectedFlywayVersion",
                ".runtime\\postgres-rehearsal",
                "pg_dump",
                "--format=custom",
                "--no-owner",
                "--no-privileges",
                "pg_restore",
                "--exit-on-error",
                "TargetContainerId -eq $SourceContainerId",
                "The source PayFlow app is running and may write to PostgreSQL"
            )
            .doesNotContain(
                "git reset --hard",
                "docker compose down -v",
                "docker volume rm",
                "DROP DATABASE"
            );
    }

    @Test
    void shouldVerifyFlywayRepresentativePersistenceAndRestoredStartup()
        throws IOException {

        assertThat(Files.readString(SCRIPT))
            .contains(
                "flyway_schema_history",
                "Assert-FingerprintEqual",
                "users",
                "refresh_token_families",
                "refresh_token_records",
                "wallets",
                "payment_transactions",
                "ledger_entries",
                "outbox_events",
                "kafka_dead_letter_records",
                "kafka_dead_letter_command_audits",
                "/api/v1/system/health",
                "Restored DB startup health: HTTP"
            )
            .doesNotContain(
                "refresh_token_sessions"
            );
    }

    @Test
    void shouldDocumentEvidencePrivacyAndRecoveryLimitations()
        throws IOException {

        String guide = Files.readString(GUIDE);

        assertThat(guide)
            .contains(
                "Issue: [#173]",
                "production disaster-recovery certification",
                "point-in-time recovery or WAL archival",
                "down-migration support",
                "No row values are written",
                "random target-database passwords",
                "19` public tables",
                "Flyway latest `V24`",
                "HTTP `200`",
                "644a8d8573a7efe224d9e4e03396f1818ed0cf92d9d64a202fa46e34fa168b4f",
                "does not mark Increment 3 complete"
            );

        assertThat(Files.readString(GITIGNORE))
            .contains(".runtime/");
    }

    @Test
    void shouldMarkOnlyPostgresBackupRestoreIncrementComplete()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);

        String incrementTwo = sectionBetween(
            roadmap,
            "### Increment 2 — PostgreSQL backup and restore rehearsal",
            "### Increment 3 — Flyway clean-install and upgrade rehearsal"
        );

        String incrementThree = sectionBetween(
            roadmap,
            "### Increment 3 — Flyway clean-install and upgrade rehearsal",
            "### Increment 4 — Redis and Kafka outage/recovery operations"
        );

        assertThat(incrementTwo)
            .contains("- [x]")
            .doesNotContain("- [ ]");

        assertThat(incrementThree)
            .contains("- [ ]");

        assertThat(roadmap)
            .contains(
                "- [x] PostgreSQL backup and restore rehearsal is repeatable and passes integrity checks"
            );
    }

    @Test
    void shouldPublishOperationalContractWithoutChangingApiOrSchema()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "[PostgreSQL backup/restore operations guide](docs/operations/postgresql-backup-restore.md)",
                "Issue #173"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "PostgreSQL 17 backup and isolated-restore rehearsal",
                "issue #173",
                "without changing runtime API, security, or Flyway schema behavior"
            );

        assertThat(Files.readString(API_BASELINE))
            .contains(
                "**30 canonical HTTP operations**",
                "**28 unique route paths**"
            );
    }

    private static String sectionBetween(
        String source,
        String startMarker,
        String endMarker
    ) {

        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());

        assertThat(start)
            .as("start marker")
            .isGreaterThanOrEqualTo(0);

        assertThat(end)
            .as("end marker")
            .isGreaterThan(start);

        return source.substring(start, end);
    }
}
