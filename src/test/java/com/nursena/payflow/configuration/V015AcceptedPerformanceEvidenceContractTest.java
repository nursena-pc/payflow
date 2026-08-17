package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015AcceptedPerformanceEvidenceContractTest {

    private static final Path EVIDENCE = Path.of(
        "docs",
        "performance",
        "evidence",
        "2026-08-17-protected-workflow-c1f5001.md"
    );

    @Test
    void shouldBindAcceptedEvidenceToTheMeasuredProtectedGreenCommit()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);

        assertThat(evidence)
            .contains(
                "**ACCEPTED**",
                "c1f5001b3709225d4865af60c621e2595cc58b59",
                "k6 v2.1.0",
                "Generalized abuse protection: enabled",
                "Run identifier: `20260817T185447Z`"
            );
    }

    @Test
    void shouldRecordFrozenPerformanceAndSecurityOutcomes()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);

        assertThat(evidence)
            .contains(
                "Steady-state verdict: **ACCEPTED**.",
                "p95 request duration | <= 750 ms | 7.252 ms | PASS",
                "p99 request duration | <= 1500 ms | 9.600 ms | PASS",
                "First saturation: **not observed through 80 iterations/s**.",
                "Overload at 120 iterations/s: **not saturated**.",
                "Recovery: **0.012 seconds**",
                "identity-only blocked decisions: **0**",
                "dependency-bypass decisions: **0**"
            );
    }

    @Test
    void shouldKeepCommittedEvidenceSanitizedAndBounded()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);

        assertThat(evidence)
            .contains(
                "No reusable runtime identity",
                "Raw k6 summaries remain ignored",
                "registration experiment",
                "`ACTIVATE` or `DEFER`"
            )
            .doesNotContain(
                "@",
                "Bearer ",
                "eyJ",
                "password=",
                "token=",
                "redis://",
                "$MeasuredCommit",
                "$ExpectedBranch",
                "$ExpectedRunId"
            );
    }
}
