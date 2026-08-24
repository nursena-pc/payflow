package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class V016RedisKafkaOutageRecoveryContractTest {

    private static final Path SCRIPT =
        Path.of(
            "scripts",
            "operations",
            "redis-kafka-outage-recovery-rehearsal.ps1"
        );

    private static final Path GUIDE =
        Path.of(
            "docs",
            "operations",
            "redis-kafka-outage-recovery.md"
        );

    private static final Path REDIS_TEST =
        Path.of(
            "src",
            "test",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "V016RedisOutageRecoveryRehearsalTest.java"
        );

    private static final Path KAFKA_TEST =
        Path.of(
            "src",
            "test",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "V016KafkaOutageRecoveryRehearsalTest.java"
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

    @Test
    void shouldCommitRepeatableIsolatedFocusedRehearsal()
        throws IOException {

        String script = Files.readString(SCRIPT);

        assertThat(script)
            .contains(
                "V016RedisOutageRecoveryRehearsalTest",
                "V016KafkaOutageRecoveryRehearsalTest",
                "Resolve-Java21",
                "JAVA_HOME",
                "Docker server",
                ".runtime\\dependency-outage-recovery",
                "git diff --check",
                "Focused rehearsal result must be exactly 2 / 0 / 0 / 0"
            )
            .doesNotContain(
                "git reset --hard",
                "git clean",
                "docker compose down",
                "docker volume rm",
                "DROP DATABASE",
                "flyway repair",
                "-FilePath 'java'"
            );
    }

    @Test
    void shouldVerifyRedisFailClosedAbuseAndAutomaticRecovery()
        throws IOException {

        String source = Files.readString(REDIS_TEST);

        assertThat(source)
            .contains(
                "postgres:17-alpine",
                "redis:8-alpine",
                "registration.enabled=false",
                "password-recovery-request.enabled=true",
                "password-recovery-request.dependency-failure-mode=FAIL_CLOSED",
                "pauseContainerCmd",
                "unpauseContainerCmd",
                "LOGIN_RATE_LIMIT_UNAVAILABLE",
                "/api/v1/auth/password-recovery/requests",
                "payflow.auth.login.rate_limit.redis.failures",
                "payflow.security.abuse_protection.redis.failures",
                "passwordRecoveryCredentialCount",
                "mailOutboxCount",
                "durableUserFingerprint",
                "awaitApplicationLoginRecovery",
                "example.invalid"
            );
    }

    @Test
    void shouldVerifyKafkaDurabilityDltReplayAndAmbiguousAcknowledgement()
        throws IOException {

        String source = Files.readString(KAFKA_TEST);

        assertThat(source)
            .contains(
                "postgres:17-alpine",
                "apache/kafka:4.1.2",
                "pauseContainerCmd",
                "unpauseContainerCmd",
                "ObjectMapper",
                "assertJsonEquivalent",
                "PENDING",
                "PUBLISHED",
                "kafka_dead_letter_records",
                "REPLAY_FAILED",
                "REPLAYED",
                "awaitReplayAttempt",
                "REPLAY_ORIGIN_ID",
                "REPLAY_ATTEMPT",
                "processedCount",
                "auditCount",
                "payment_transactions",
                "ledger_entries"
            );
    }

    @Test
    void shouldDocumentSafeOperationsPrivacyAndDeliveryLimitations()
        throws IOException {

        String guide = Files.readString(GUIDE);

        assertThat(guide.replaceAll("\\s+", " "))
            .contains(
                "PostgreSQL remains the durable system of record",
                "Registration remains the reviewed `DEFER` case",
                "acknowledgement ambiguity",
                "at-least-once delivery boundary",
                "must not manually mark",
                "Do not disable the login limiter",
                "single durable idempotency/audit boundary",
                "must not contain real email addresses",
                "zero-data-loss",
                "does not certify"
            );
    }

    @Test
    void shouldRetainIncrementFourCompletionInPublishedRoadmap()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);

        String incrementFour = sectionBetween(
            roadmap,
            "### Increment 4",
            "### Increment 5"
        );

        assertThat(incrementFour)
            .contains("- [x]")
            .doesNotContain("- [ ]");

        assertThat(roadmap)
            .contains(
                "- [x] Redis and Kafka outage/recovery procedures are documented and verified against existing failure contracts",
                "### Increment 5",
                "- [x] Compare implemented `/api/v1` behavior with OpenAPI and Postman contracts",
                "### Increment 6"
            );

        assertThat(Files.readString(README))
            .contains(
                "[Issue #178]",
                "[Redis/Kafka outage-recovery operations guide](docs/operations/redis-kafka-outage-recovery.md)"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "issue #178",
                "acknowledgement ambiguity"
            );
    }

    @Test
    void shouldPreserveFrozenApiAndMigrationBaseline()
        throws IOException {

        assertThat(Files.readString(API_BASELINE))
            .contains(
                "**30 canonical HTTP operations**",
                "**28 unique route paths**",
                "Registration remains the reviewed `DEFER` case"
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
                        name.startsWith(
                            "V" + version + "__"
                        )
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
