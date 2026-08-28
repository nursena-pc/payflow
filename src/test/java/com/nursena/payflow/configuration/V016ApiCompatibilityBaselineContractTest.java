package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class V016ApiCompatibilityBaselineContractTest {

    private static final Path BASELINE =
        Path.of("docs", "api-v1-compatibility.md");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

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

    private static final Path OPENAPI =
        Path.of(
            "src",
            "test",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "integration",
            "OpenApiJsonContractIntegrationTest.java"
        );

    private static final Path OPENAPI_CONFIGURATION =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "configuration",
            "OpenApiConfiguration.java"
        );

    private static final Path ARCHITECTURE =
        Path.of("docs", "architecture.md");

    private static final List<String>
        CANONICAL_OPERATIONS = List.of(
            "GET` | `/api/v1/system/health",
            "POST` | `/api/v1/auth/register",
            "POST` | `/api/v1/auth/login",
            "POST` | `/api/v1/auth/mfa/challenges/confirm",
            "POST` | `/api/v1/auth/email-verification/requests",
            "POST` | `/api/v1/auth/email-verification/confirm",
            "POST` | `/api/v1/auth/password-recovery/requests",
            "POST` | `/api/v1/auth/password-recovery/confirm",
            "POST` | `/api/v1/auth/refresh",
            "POST` | `/api/v1/auth/logout",
            "POST` | `/api/v1/auth/logout-all",
            "GET` | `/api/v1/users/me",
            "GET` | `/api/v1/users/me/mfa",
            "POST` | `/api/v1/users/me/mfa/enrollment",
            "POST` | `/api/v1/users/me/mfa/enrollment/confirm",
            "DELETE` | `/api/v1/users/me/mfa/enrollment",
            "POST` | `/api/v1/users/me/step-up/grants",
            "POST` | `/api/v1/users/me/mfa/recovery-codes/rotation",
            "DELETE` | `/api/v1/users/me/mfa",
            "POST` | `/api/v1/wallets",
            "GET` | `/api/v1/wallets/me",
            "POST` | `/api/v1/wallets/me/top-ups",
            "POST` | `/api/v1/transfers",
            "GET` | `/api/v1/transactions/me",
            "GET` | `/api/v1/operations/kafka/dead-letters",
            "GET` | `/api/v1/operations/kafka/dead-letters/{recordId}",
            "POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/replay",
            "POST` | `/api/v1/operations/kafka/dead-letters/{recordId}/discard",
            "GET` | `/api/v1/operations/kafka/dead-letter-command-audits",
            "GET` | `/api/v1/operations/kafka/dead-letter-command-audits/{commandId}"
        );

    @Test
    void shouldFreezeCanonicalThirtyOperationInventory()
        throws IOException {

        String baseline = Files.readString(BASELINE);

        assertThat(baseline)
            .contains(
                "**30 canonical HTTP operations**",
                "**28 unique route paths**",
                "HTTP method + normalized route path"
            );

        assertThat(CANONICAL_OPERATIONS)
            .hasSize(30);

        assertThat(baseline)
            .contains(CANONICAL_OPERATIONS.toArray(String[]::new));

        assertThat(Files.readString(README))
            .contains(
                "[API compatibility baseline](docs/api-v1-compatibility.md)",
                "explicit reviewed compatibility checkpoint"
            );
    }

    @Test
    void shouldRecordOpenApiAndPostmanComparison()
        throws IOException {

        String baseline = Files.readString(BASELINE);
        String openApiTest = Files.readString(OPENAPI);

        assertThat(baseline)
            .contains(
                "same **28 unique",
                "**25 unique canonical `/api/v1` operations**",
                "POST /api/v1/auth/email-verification/confirm",
                "POST /api/v1/auth/refresh",
                "POST /api/v1/auth/logout",
                "POST /api/v1/auth/logout-all",
                "DELETE /api/v1/users/me/mfa/enrollment",
                "known executable-workflow coverage gaps"
            );

        assertThat(openApiTest)
            .contains(
                "shouldExposeExactlyThePublicApiPaths",
                "KAFKA_DEAD_LETTER_COMMAND_AUDITS_PATH",
                "MFA_ENROLLMENT_PATH",
                "LOGOUT_ALL_PATH"
            );
    }

    @Test
    void shouldFreezeSecurityPrivacyAndFailureBoundaries()
        throws IOException {

        String baseline = Files.readString(BASELINE);
        String security = Files.readString(SECURITY);

        assertThat(security)
            .contains(
                "\"/api/v1/auth/register\"",
                "\"/api/v1/auth/login\"",
                "\"/api/v1/auth/email-verification/requests\"",
                "\"/api/v1/auth/password-recovery/requests\"",
                "\"/api/v1/operations/**\"",
                ".anyRequest()",
                ".denyAll()"
            );

        assertThat(baseline)
            .contains(
                "positive `Retry-After`",
                "fail-closed `503`",
                "empty `202 Accepted`",
                "Registration remains the reviewed `DEFER` case",
                "Trusted-client",
                "low-cardinality",
                "single-use",
                "successful replay cannot create a second financial movement",
                "simulated-money modular monolith"
            );
    }

    @Test
    void shouldRecordResolvedDocumentationAlignment()
        throws IOException {

        String baseline = Files.readString(BASELINE);
        String architecture = Files.readString(ARCHITECTURE);
        String openApiConfiguration =
            Files.readString(OPENAPI_CONFIGURATION);

        assertThat(architecture)
            .contains(
                "Transactional outbox persistence is implemented",
                "`abuseprotection`",
                "`eventprocessing`",
                "`maildelivery`",
                "`observability`",
                "`outbox`"
            )
            .doesNotContain(
                "Transactional outbox persistence is planned for a later milestone",
                "notification",
                "future messaging integrations"
            );

        assertThat(openApiConfiguration)
            .contains("API_VERSION")
            .doesNotContain("\"0.2.0\"");

        assertThat(baseline)
            .contains(
                "Increment 5 alignment",
                "**30 unique canonical `/api/v1` operations**",
                "PayFlow.api-compatibility.postman_collection.json",
                "`0.16.0-SNAPSHOT`"
            );
    }
    @Test
    void shouldKeepCompatibilityCheckpointHistoricalDuringRecoveryWork()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);

        assertThat(roadmap)
            .contains(
                "- [x] Open the Maven development line at `0.16.0-SNAPSHOT` through a protected PR",
                "- [x] Inventory implemented `/api/v1` endpoints, status/error contracts, OpenAPI descriptions, and executable Postman flows",
                "- [x] Define the v1 compatibility boundary so existing `/api/v1` request, response, and error semantics cannot change silently",
                "- [x] Freeze existing security, privacy, fail-closed, simulated-money, and modular-monolith boundaries",
                "- [x] Inventory stale architecture and release documentation before changing it",
                "- [x] Add executable development contracts for the approved v0.16.0 stabilization scope",
                "- [x] `0.16.0-SNAPSHOT` development baseline is opened through a protected PR",
                "- [x] `/api/v1` compatibility boundary is documented and executable where practical"
            )
            .contains(
                "### Increment 2 — PostgreSQL backup and restore rehearsal",
                "- [x] Define one repeatable local backup procedure for the PostgreSQL system of record"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "issue #171",
                "without changing runtime behavior"
            );
    }
}