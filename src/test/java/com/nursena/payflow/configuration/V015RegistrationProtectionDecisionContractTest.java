package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015RegistrationProtectionDecisionContractTest {

    private static final Path EVIDENCE = Path.of(
        "docs",
        "performance",
        "evidence",
        "2026-08-17-registration-defer-f94ffa8.md"
    );

    private static final Path ROADMAP = Path.of(
        "docs",
        "roadmap.md"
    );

    @Test
    void shouldBindDecisionToReviewedRegistrationEvidence()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);

        assertThat(evidence)
            .contains(
                "**DEFER**",
                "f94ffa8870439abb1e17d8ae46a1cf16abe8c572",
                "`20260817T204657Z`",
                "Registration protection wired during measurement: false"
            );
    }

    @Test
    void shouldRecordMeasuredOutcomeWithoutClaimingSaturation()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);

        assertThat(evidence)
            .contains(
                "Baseline p95: **262.373 ms**",
                "Ramp-16 p95: **274.137 ms**",
                "Ramp-16 p99: **282.008 ms**",
                "not observed through 16 registrations/s",
                "Recovery: **0.023 seconds**",
                "Decision: **DEFER**"
            );
    }

    @Test
    void shouldPreserveConditionalActivationBoundary()
        throws IOException {

        String evidence = Files.readString(EVIDENCE);
        String normalizedEvidence = evidence.replaceAll("\\s+", " ");

        assertThat(normalizedEvidence)
            .contains(
                "resource-exhaustion risk",
                "`ACTIVATE`",
                "<=10% protected-versus-unprotected",
                "not evaluated because no registration protection",
                "mandatory only if future evidence supports"
            );
    }

    @Test
    void shouldPreserveRegistrationDecisionAfterV015Publication()
        throws IOException {

        String roadmap = Files.readString(ROADMAP);
        int v015Start = roadmap.indexOf("## v0.15.0");
        int increment6Start = roadmap.indexOf(
            "### Increment 6",
            v015Start
        );
        int increment7Start = roadmap.indexOf(
            "### Increment 7",
            increment6Start + 1
        );

        assertThat(v015Start).isGreaterThanOrEqualTo(0);
        assertThat(increment6Start).isGreaterThan(v015Start);
        assertThat(increment7Start).isGreaterThan(increment6Start);

        String increment6 = roadmap.substring(
            increment6Start,
            increment7Start
        );

        assertThat(increment6)
            .contains(
                "Reproducible load and performance evidence",
                "- [x] Define latency, throughput, concurrency, saturation, and overload budgets",
                "- [x] Add reproducible load scenarios for representative protected workflows",
                "- [x] Record environment, dataset, duration, warm-up, measurement method, and limitations",
                "- [x] Verify abuse protection remains effective under concurrent and overload conditions",
                "- [x] Keep load tooling outside the normal unit-test lifecycle while retaining repeatable commands",
                "evidence-backed `DEFER`"
            );

        String increment7 = roadmap.substring(increment7Start);

        assertThat(increment7)
            .contains(
                "Contract alignment and release preparation",
                "- [x] Align OpenAPI, Postman, README, changelog, ADRs, threat model, and operations guidance",
                "- [x] Add focused unit, Redis, HTTP, concurrency, redaction, and dependency-failure tests",
                "- [x] Prepare versioned release notes",
                "- [x] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head",
                "- [x] Record immutable publication evidence after protected merge and publication",
                "- [x] the v0.15.0 tag, JAR, checksum, and GitHub Release are published"
            );
    }
}
