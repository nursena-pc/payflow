package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100ObservabilityPerformanceContractTest {

    private static final Path CURRENT =
        Path.of(
            "docs",
            "v1-observability-performance.md"
        );

    private static final Path EVIDENCE =
        Path.of(
            "docs",
            "performance",
            "evidence",
            "2026-08-27-protected-workflow-8a49e8b.md"
        );

    private static final Path RECORDER =
        Path.of(
            "performance",
            "k6",
            "record-protected-evidence.ps1"
        );

    private static final Path WORKLOAD =
        Path.of(
            "performance",
            "k6",
            "lib",
            "workload.js"
        );

    private static final Path SCENARIO =
        Path.of(
            "performance",
            "k6",
            "scenarios",
            "account-action-evidence.js"
        );

    @Test
    void shouldRecordCurrentV1ObservabilityPerformanceClosure()
        throws IOException {

        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(current)
            .contains(
                "Status: Active v1.0.0 release-candidate observability/performance contract",
                "Tracking issue: #197",
                "Baseline: `8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3`",
                "Run identifier: `20260827T152405Z`",
                "No runtime observability defect",
                "Steady-state verdict: **ACCEPTED**",
                "without runtime expansion"
            );
    }

    @Test
    void shouldAnchorPromotedEvidenceToExactMeasuredCommit()
        throws IOException {

        String evidence =
            normalizeWhitespace(
                Files.readString(EVIDENCE)
            );

        assertThat(evidence)
            .contains(
                "# Accepted v1.0.0 protected-workflow performance evidence",
                "Git commit: 8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3",
                "Source candidate Markdown SHA-256: `183cfb6a31f410d9099c95ea3a02d76d44ce672cf0362a04fc04415342eaa994`",
                "Source candidate JSON SHA-256: `52c720bf0e458634153fd09f31a0fb8a4bb255106214474cc3871d33584bd351`",
                "Steady-state accepted: **True**",
                "Recovery: **0.038 seconds**",
                "not a production capacity certification"
            );
    }

    @Test
    void shouldPreserveReviewedK6OwnershipAndBudgets()
        throws IOException {

        String recorder = Files.readString(RECORDER);
        String workload = Files.readString(WORKLOAD);
        String scenario = Files.readString(SCENARIO);

        assertThat(recorder)
            .contains(
                "$SteadyP95BudgetMs = 750.0",
                "$SteadyP99BudgetMs = 1500.0",
                "$SteadyUnexpectedFailureBudget = 0.005",
                "$SteadyMinimumAchievementRatio = 0.95",
                "$RecoveryBudgetSeconds = 30",
                "account-action-evidence"
            );

        assertThat(workload)
            .contains(
                "PAYFLOW_K6_EVIDENCE_RATE",
                "PAYFLOW_K6_EVIDENCE_DURATION",
                "PAYFLOW_K6_EVIDENCE_PRE_ALLOCATED_VUS",
                "PAYFLOW_K6_EVIDENCE_MAX_VUS",
                "exec: 'protectedWorkflow'",
                "exec: 'healthProbe'"
            );

        assertThat(scenario)
            .contains(
                "payflow_evidence_request_duration",
                "payflow_unexpected_failures",
                "payflow_health_probe_failures",
                "/api/v1/auth/email-verification/requests",
                "/api/v1/system/health"
            );
    }

    @Test
    void shouldKeepV1PerformanceClaimsBounded()
        throws IOException {

        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(current)
            .contains(
                "bounded synthetic developer-workstation observations only",
                "not production capacity measurements",
                "production SLO/SLA commitments",
                "arbitrary performance tuning",
                "weakened quota, retry, security, or failure-mode behavior",
                "production-capacity, regulatory-certification, or real-money claims"
            );
    }

    @Test
    void shouldCloseRoadmapAndPublicReleaseCandidateState()
        throws IOException {

        String roadmap =
            normalizeWhitespace(
                Files.readString(
                    Path.of(
                        "docs",
                        "roadmap.md"
                    )
                )
            );

        String readme =
            normalizeWhitespace(
                Files.readString(
                    Path.of("README.md")
                )
            );

        String changelog =
            normalizeWhitespace(
                Files.readString(
                    Path.of("CHANGELOG.md")
                )
            );

        assertThat(roadmap)
            .contains(
                "Observability/performance closure issue: [#197]",
                "- [x] Verify structured logging, request/correlation IDs, sensitive-value redaction, metrics, dashboards, and alerts remain coherent",
                "- [x] Re-run the repository-approved v1 performance evidence",
                "- [x] Record bounded latency, throughput, saturation, overload, and recovery evidence with environment assumptions",
                "- [x] Do not convert local or synthetic evidence into production-capacity claims",
                "20260827T152405Z",
                "0.038",
                "docs/v1-observability-performance.md",
                "docs/performance/evidence/2026-08-27-protected-workflow-8a49e8b.md"
            );

        assertThat(readme)
            .contains(
                "observability/performance release-budget closure by [issue #197]",
                "repository-approved protected-workflow performance recorder",
                "v1 observability/performance contract",
                "accepted v1 performance evidence",
                "not production-capacity certification"
            );

        assertThat(changelog)
            .contains(
                "observability/performance release-budget review through issue #197",
                "fresh accepted developer-workstation evidence",
                "8a49e8bcd05ba64fd07b87bdeca5dc415e87dda3",
                "without making a production-capacity claim"
            );
    }
    private static String normalizeWhitespace(
        String value
    ) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
