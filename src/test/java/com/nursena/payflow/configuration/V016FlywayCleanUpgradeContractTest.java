package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class V016FlywayCleanUpgradeContractTest {

    private static final Path SCRIPT =
        Path.of(
            "scripts",
            "operations",
            "flyway-clean-upgrade-rehearsal.ps1"
        );

    private static final Path GUIDE =
        Path.of(
            "docs",
            "operations",
            "flyway-clean-upgrade.md"
        );

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path API_BASELINE =
        Path.of("docs", "api-v1-compatibility.md");

    private static final Path MIGRATIONS =
        Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration"
        );

    private static final List<String> V17_TABLES = List.of(
        "users",
        "wallets",
        "payment_transactions",
        "ledger_entries",
        "outbox_events",
        "processed_kafka_events",
        "transfer_completed_event_audits",
        "kafka_dead_letter_records",
        "kafka_dead_letter_command_audits",
        "refresh_token_families",
        "refresh_token_records",
        "account_action_credentials",
        "mail_outbox_messages"
    );

    @Test
    void shouldCommitIsolatedPostgres17CleanInstallAndUpgradeProcedure()
        throws IOException {

        String script = Files.readString(SCRIPT);

        assertThat(script)
            .contains(
                "postgres:17-alpine",
                "=== 4. CLEAN INSTALL: EMPTY POSTGRESQL 17 -> V24 ===",
                "=== 6. IMMUTABLE HISTORICAL V0.13.0 BASELINE -> V17 ===",
                "=== 8. UPGRADE V17 -> V24 WITH CURRENT APP ===",
                "payflow_clean",
                "payflow_upgrade",
                ".runtime\\flyway-rehearsal",
                "ExternalWorktreeRoot",
                "worktree','add','--detach",
                "Resolve-Java21",
                "JAVA_HOME",
                "$startInfo.FileName = $javaExecutable"
            )
            .doesNotContain(
                "docker compose down -v",
                "docker volume rm",
                "DROP DATABASE",
                "git reset --hard",
                "-FilePath 'java'"
            );
    }

    @Test
    void shouldPinApprovedImmutableV013BaselineAndRejectHistoricalDrift()
        throws IOException {

        String script = Files.readString(SCRIPT);
        String guide = Files.readString(GUIDE);

        assertThat(script)
            .contains(
                "v0.13.0",
                "726f631a0de800870813ccb0c00b2676eb5d172b",
                "Current migration count is not 24",
                "v0.13.0 migration count is not 17",
                "Historical blob drift",
                "V18..V24 delta"
            );

        assertThat(guide)
            .contains(
                "immutable `v0.13.0` / V17",
                "V18 through V24",
                "`v0.14.0` and `v0.15.0`",
                "would not exercise a real schema upgrade"
            );
    }

    @Test
    void shouldSeedAndPreserveEveryV17PersistenceTable()
        throws IOException {

        String script = Files.readString(SCRIPT);

        assertThat(V17_TABLES)
            .hasSize(13);

        assertThat(script)
            .contains(
                V17_TABLES.toArray(String[]::new)
            )
            .contains(
                "13 / 13 V17 data tables seeded.",
                "Fingerprints",
                "Assert-Fingerprints",
                "to_jsonb(t)::text",
                "Content fingerprint changed"
            );

        assertThat(Files.readString(GUIDE))
            .contains(
                "All 13 non-Flyway V17 data tables",
                "row count plus an MD5 digest",
                "synthetic"
            );
    }

    @Test
    void shouldVerifyExactCurrentHistoryConstraintsAndApplicationHealth()
        throws IOException {

        String script = Files.readString(SCRIPT);

        assertThat(script)
            .contains(
                "flyway_schema_history",
                "Assert-Flyway",
                "History-Prefix-Digest",
                "mfa_authenticators",
                "mfa_login_challenges",
                "mfa_recovery_codes",
                "step_up_grants",
                "account_security_audits",
                "MFA_DISABLED",
                "RECOVERY_CODES_ROTATED",
                "/api/v1/system/health",
                "Current clean-install app",
                "Current upgrade app"
            );
    }

    @Test
    void shouldDocumentForwardOnlyRecoveryPrivacyAndNoRepairBoundary()
        throws IOException {

        String script = Files.readString(SCRIPT);
        String guide = Files.readString(GUIDE);

        assertThat(script.toLowerCase())
            .doesNotContain(
                "flyway repair",
                "flyway:repair",
                "flyway undo"
            );

        assertThat(guide)
            .contains(
                "forward migrations",
                "PayFlow does not",
                "claim automated down-migration support",
                "PostgreSQL backup/restore procedure",
                "production RPO/RTO",
                "7709c3e1f56d8d5128cbcd98318b5a1d0b8aaab05d1ce41196646dd2ce7d585e",
                "must not contain real email addresses",
                "`JAVA_HOME` must point to the Java 21 JDK",
                "never falls back to an unrelated bare `java`"
            );
    }

    @Test
    void shouldMarkIncrementCompleteWithoutChangingApiOrMigrationSet()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);

        String incrementThree = sectionBetween(
            roadmap,
            "### Increment 3 — Flyway clean-install and upgrade rehearsal",
            "### Increment 4 — Redis and Kafka outage/recovery operations"
        );

        assertThat(incrementThree)
            .contains("- [x]")
            .doesNotContain("- [ ]");

        assertThat(roadmap)
            .contains(
                "- [x] clean-install and previous-release-to-current Flyway rehearsals pass"
            );

        assertThat(Files.readString(README))
            .contains(
                "[Issue #175]",
                "[Flyway clean-install / upgrade operations guide](docs/operations/flyway-clean-upgrade.md)"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "issue #175",
                "without changing runtime API, security, or migration schema content"
            );

        assertThat(Files.readString(API_BASELINE))
            .contains(
                "**30 canonical HTTP operations**",
                "**28 unique route paths**"
            );

        List<String> migrationNames;
        try (var paths = Files.list(MIGRATIONS)) {
            migrationNames = paths
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("V\\d+__.+\\.sql"))
                .sorted()
                .toList();
        }

        assertThat(migrationNames)
            .hasSize(24);

        IntStream.rangeClosed(1, 24)
            .forEach(version ->
                assertThat(migrationNames)
                    .anyMatch(name ->
                        name.startsWith("V" + version + "__")
                    )
            );
    }

    private static String sectionBetween(
        String source,
        String startMarker,
        String endMarker
    ) {

        int start = source.indexOf(startMarker);
        int end = source.indexOf(
            endMarker,
            start + startMarker.length()
        );

        assertThat(start)
            .as("start marker")
            .isGreaterThanOrEqualTo(0);

        assertThat(end)
            .as("end marker")
            .isGreaterThan(start);

        return source.substring(start, end);
    }
}
