package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100FinancialMessagingIntegrityContractTest {

    private static final Path CURRENT =
        Path.of(
            "docs",
            "v1-financial-messaging-integrity.md"
        );

    private static final Path ARCHITECTURE =
        Path.of(
            "docs",
            "architecture.md"
        );

    private static final Path RECOVERY =
        Path.of(
            "docs",
            "operations",
            "redis-kafka-outage-recovery.md"
        );

    private static final Path ADR_REPLAY =
        Path.of(
            "docs",
            "adr",
            "0005-controlled-kafka-dead-letter-replay.md"
        );

    private static final Path ADR_AUDIT_QUERY =
        Path.of(
            "docs",
            "adr",
            "0008-query-kafka-dead-letter-command-audits.md"
        );

    private static final Path COMMAND_CONTROLLER =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "eventprocessing",
            "adapter",
            "in",
            "web",
            "KafkaDeadLetterCommandController.java"
        );

    private static final Path AUDIT_CONTROLLER =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "eventprocessing",
            "adapter",
            "in",
            "web",
            "KafkaDeadLetterCommandAuditController.java"
        );

    private static final Path SECURITY =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "SecurityConfiguration.java"
        );

    private static final Path ROADMAP =
        Path.of(
            "docs",
            "roadmap.md"
        );

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    @Test
    void shouldRecordCurrentV1FinancialMessagingIntegrity()
        throws IOException {

        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(current)
            .contains(
                "Status: Active v1.0.0 release-candidate integrity contract",
                "Tracking issue: #194",
                "Baseline: `315488e88f2b69c56d77fc64b49dfc1c6497649f`",
                "119 test classes",
                "564 focused tests",
                "PostgreSQL remains the durable system of record",
                "Kafka delivery remains at-least-once",
                "Exactly-once delivery is not claimed",
                "No runtime integrity defect was identified"
            );
    }

    @Test
    void shouldAnchorDurableFinancialAndRecoveryBoundaries()
        throws IOException {

        String architecture =
            normalizeWhitespace(
                Files.readString(ARCHITECTURE)
            );

        String recovery =
            normalizeWhitespace(
                Files.readString(RECOVERY)
            );

        assertThat(architecture)
            .contains(
                "Wallet mutation, payment-transaction persistence, transaction-state completion, and double-entry ledger persistence execute in one PostgreSQL transaction.",
                "PostgreSQL remains the system of record for transfer, ledger, outbox, dead-letter, replay, and processing evidence where designed.",
                "Automatic retry is not currently applied to financial mutations.",
                "at-least-once delivery boundary"
            );

        assertThat(recovery)
            .contains(
                "PostgreSQL remains the durable system of record.",
                "This is an at-least-once delivery boundary, not a zero-duplicate delivery claim.",
                "Operators must not manually mark an outbox or dead-letter record successful solely because a client-side Kafka timeout occurred.",
                "Do not edit PostgreSQL outbox/DLT rows manually to manufacture success."
            );
    }

    @Test
    void shouldFrameHistoricalAdrsAgainstCurrentImplementation()
        throws IOException {

        String replay =
            normalizeWhitespace(
                Files.readString(ADR_REPLAY)
            );

        String auditQuery =
            normalizeWhitespace(
                Files.readString(ADR_AUDIT_QUERY)
            );

        assertThat(replay)
            .contains(
                "Current-state note (v1.0.0 release-candidate)",
                "historical decision body below is retained unchanged",
                "PAYFLOW_OPERATIONS",
                "The initial persistence and intake implementation exposes no replay HTTP endpoint."
            );

        assertThat(auditQuery)
            .contains(
                "Current-state note (v1.0.0 release-candidate)",
                "historical future-tense decision body below is retained unchanged",
                "operator-only command-audit list and timeline endpoints",
                "They are not currently available through an explicit application query boundary."
            );
    }

    @Test
    void shouldAnchorImplementedOperationsControlPlane()
        throws IOException {

        String commandController =
            Files.readString(COMMAND_CONTROLLER);

        String auditController =
            Files.readString(AUDIT_CONTROLLER);

        String security =
            Files.readString(SECURITY);

        assertThat(commandController)
            .contains(
                "\"/api/v1/operations/kafka/dead-letters\"",
                "@PostMapping(\"/{recordId}/replay\")",
                "@PostMapping(\"/{recordId}/discard\")"
            );

        assertThat(auditController)
            .contains(
                "\"/api/v1/operations/kafka/\"",
                "\"dead-letter-command-audits\"",
                "@GetMapping"
            );

        assertThat(security)
            .contains(
                "\"/api/v1/operations/**\"",
                "OperationsAuthorities.OPERATIONS"
            );
    }

    @Test
    void shouldCloseRoadmapCheckpointWithoutRuntimeExpansion()
        throws IOException {

        String roadmap =
            Files.readString(ROADMAP);

        String readme =
            normalizeWhitespace(
                Files.readString(README)
            );

        String changelog =
            normalizeWhitespace(
                Files.readString(CHANGELOG)
            );

        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(roadmap)
            .contains(
                "Financial/messaging integrity closure issue: [#194]",
                "- [x] Re-verify transaction, idempotency, wallet, double-entry ledger, outbox, Kafka, DLQ/replay, and audit guarantees",
                "- [x] Verify PostgreSQL remains the durable source of truth where designed",
                "- [x] Re-verify duplicate, retry, concurrency, and dependency-failure consistency paths",
                "- [x] Keep messaging architecture unchanged unless a separately verified release blocker requires correction",
                "119 discovered transaction/wallet/ledger/outbox/event-processing test classes",
                "564 focused tests",
                "No runtime integrity defect was identified",
                "`docs/v1-financial-messaging-integrity.md`"
            );

        assertThat(readme)
            .contains(
                "PayFlow v1.0.0 is the latest tagged and published release",
                "authentication/security lifecycle closure #192",
                "financial/messaging integrity closure #194",
                "release preparation #203",
                "immutable publication #207",
                "issue #208",
                "v1 financial/messaging integrity contract"
            )
            .doesNotContain(
                "Checkpoints 1 through 6 are complete",
                "release preparation is tracked by issue #203"
            );

        assertThat(changelog)
            .contains(
                "financial/messaging integrity contract through issue #194",
                "119-class / 564-test focused diagnostic",
                "leaving runtime, public API, migration/schema, messaging topology, and financial behavior unchanged"
            );

        assertThat(current)
            .contains(
                "CP3 does not add or activate",
                "new wallet, transfer, payment, or public API features",
                "microservice extraction or distributed-transaction redesign",
                "Kafka topology redesign or broker replacement",
                "exactly-once delivery guarantees",
                "database schema or migration changes without a verified blocker",
                "regulatory, production-certification, or real-money claims"
            );
    }

    private static String normalizeWhitespace(
        String value
    ) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
