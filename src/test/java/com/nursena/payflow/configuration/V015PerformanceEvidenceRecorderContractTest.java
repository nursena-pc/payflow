package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015PerformanceEvidenceRecorderContractTest {

    private static final Path COMPOSE =
        Path.of("performance", "k6", "compose.yml");
    private static final Path WORKLOAD =
        Path.of("performance", "k6", "lib", "workload.js");
    private static final Path RUNNER =
        Path.of("performance", "k6", "run.ps1");
    private static final Path EVIDENCE_SCENARIO =
        Path.of(
            "performance",
            "k6",
            "scenarios",
            "account-action-evidence.js"
        );
    private static final Path RECORDER =
        Path.of("performance", "k6", "record-protected-evidence.ps1");
    private static final Path SCENARIOS =
        Path.of("performance", "k6", "SCENARIOS.md");

    @Test
    void shouldKeepEvidenceExecutionOptionsNamespaced() throws IOException {
        String compose = Files.readString(COMPOSE);
        String workload = Files.readString(WORKLOAD);

        assertThat(compose)
            .contains(
                "PAYFLOW_K6_EVIDENCE_RATE",
                "PAYFLOW_K6_EVIDENCE_DURATION",
                "PAYFLOW_K6_EVIDENCE_PRE_ALLOCATED_VUS",
                "PAYFLOW_K6_EVIDENCE_MAX_VUS"
            );

        assertThat(workload)
            .contains(
                "evidenceWorkloadOptions",
                "executor: 'constant-arrival-rate'",
                "PAYFLOW_K6_EVIDENCE_RATE",
                "PAYFLOW_K6_EVIDENCE_DURATION",
                "summaryTrendStats: ['med', 'p(95)', 'p(99)']"
            );
    }

    @Test
    void shouldMeasureProtectedLatencySeparatelyFromHealthProbe()
        throws IOException {

        String scenario = Files.readString(EVIDENCE_SCENARIO);

        assertThat(scenario)
            .contains(
                "new Counter('payflow_evidence_requests')",
                "new Trend('payflow_evidence_request_duration', true)",
                "new Rate('payflow_unexpected_failures')",
                "new Rate('payflow_health_probe_failures')",
                "export function protectedWorkflow()",
                "export function healthProbe()",
                "/api/v1/auth/email-verification/requests",
                "/api/v1/system/health"
            )
            .doesNotContain(
                "email,",
                "tags: { email",
                "Authorization"
            );
    }

    @Test
    void shouldFreezeRecorderPhasesAndAcceptanceBudgets() throws IOException {
        String recorder = Files.readString(RECORDER);

        assertThat(recorder)
            .contains(
                "$SteadyP95BudgetMs = 750.0",
                "$SteadyP99BudgetMs = 1500.0",
                "$SteadyUnexpectedFailureBudget = 0.005",
                "$SteadyMinimumAchievementRatio = 0.95",
                "$SaturationP95Ms = 1500.0",
                "$SaturationUnexpectedFailureRate = 0.01",
                "$RecoveryBudgetSeconds = 30",
                "$SaturationRates = @(10, 20, 40, 80)",
                "-Rate 5",
                "-DurationSeconds 30",
                "-Rate 10",
                "-DurationSeconds 120",
                "[Math]::Ceiling([double] $FirstSaturatedRate * 1.5)",
                "120"
            );
    }

    @Test
    void shouldKeepEvidenceRawUntilSeparatePromotion() throws IOException {
        String runner = Files.readString(RUNNER);
        String recorder = Files.readString(RECORDER);
        String scenarios = Files.readString(SCENARIOS);

        assertThat(runner)
            .contains(
                "account-action-evidence",
                "SummaryExportPath",
                "--summary-export",
                "--summary-trend-stats",
                "/results/"
            );

        assertThat(recorder)
            .contains(
                "performance\\results\\evidence",
                "Accepted evidence requires a clean Git working tree.",
                "candidate-protected-workflow-evidence.json",
                "candidate-protected-workflow-evidence.md",
                "require separate review before promotion"
            )
            .doesNotContain("docs\\performance\\evidence");

        assertThat(scenarios)
            .contains(
                "Raw summaries are ignored",
                "never metric tags or evidence dimensions"
            );
    }

    @Test
    void shouldParseThePinnedK6CompatibilitySummaryExportShape()
        throws IOException {

        String recorder = Files.readString(RECORDER);
        String runner = Files.readString(RUNNER);

        assertThat(recorder)
            .contains(
                "$Summary.PSObject.Properties['metrics']",
                "$MetricsProperty.Value.PSObject.Properties[$MetricName]",
                "$MetricProperty.Value.PSObject.Properties[$ValueName]",
                "$MetricProperty.Value.PSObject.Properties['value']",
                "[StringComparison]::Ordinal"
            )
            .doesNotContain(
                "$MetricProperty.Value.values.PSObject.Properties[$ValueName]",
                "$Summary.PSObject.Properties['results']",
                "$MetricMatches[0].PSObject.Properties['values']"
            );

        assertThat(runner)
            .contains("--summary-export")
            .doesNotContain("--new-machine-readable-summary");
    }
    @Test
    void shouldCaptureJavaRuntimeMetadataWithoutNativeStderrRedirection()
        throws IOException {

        String recorder = Files.readString(RECORDER);

        assertThat(recorder)
            .contains("exec -T app java --version")
            .doesNotContain("exec -T app java -version 2>&1");
    }
    @Test
    void shouldPreserveSecurityDecisionBoundariesDuringEvidence()
        throws IOException {

        String recorder = Files.readString(RECORDER);

        assertThat(recorder)
            .contains(
                "$ClientDecisionLimit = 20",
                "redis-cli FLUSHDB",
                "AllowedDelta -gt $ClientDecisionLimit",
                "BlockedIdentityDelta -ne 0",
                "BlockedBothDelta -ne 0",
                "BypassDelta -ne 0",
                "ABUSE_PROTECTION_ENABLED = 'true'"
            )
            .doesNotContain(
                "ABUSE_PROTECTION_ENABLED = 'false'",
                "dependency_bypass = true"
            );
    }
}
