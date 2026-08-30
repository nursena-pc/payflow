package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100DevelopmentContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldCarryV100DevelopmentLineIntoReleasePreparation()
        throws IOException {

        assertThat(Files.readString(POM))
            .contains("<version>1.0.0</version>")
            .doesNotContain(
                "<version>1.0.0-SNAPSHOT</version>",
                "<version>0.16.0-SNAPSHOT</version>"
            );

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.16.0 remains the latest published release",
                "reviewed v1.0.0 release-preparation candidate uses Maven version `1.0.0`",
                "## v1.0.0 release preparation",
                "Checkpoints 1 through 6 are complete",
                "development-start #190",
                "authentication/security lifecycle closure #192",
                "financial/messaging integrity closure #194",
                "observability/performance closure #197",
                "recovery/migration/API/documentation freeze #199",
                "supply-chain/clean-environment verification #201",
                "release-preparation checkpoint is tracked by [issue #203]",
                "7712c5ccbeeee3b9cefd3324c42270e71554ea17"
            )
            .doesNotContain(
                "Issue #186 remains the publication-record checkpoint"
            );
    }

    @Test
    void shouldDefineV100ReleasePreparationPlan()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v1.0.0 — Release Preparation",
                "Tracking issue: [#189]",
                "Development-start issue: [#190]",
                "Release-preparation issue: [#203]",
                "### Checkpoint 1 — Release-candidate development baseline",
                "### Checkpoint 2 — Authentication and security lifecycle closure",
                "### Checkpoint 3 — Financial and messaging integrity guarantees",
                "### Checkpoint 4 — Observability and performance release budgets",
                "### Checkpoint 5 — Recovery, migration, API, and documentation freeze",
                "### Checkpoint 6 — Supply-chain and clean-environment release-candidate verification",
                "### Checkpoint 7 — v1.0.0 release preparation",
                "### Checkpoint 8 — Immutable v1.0.0 publication",
                "### Checkpoint 9 — Publication record and release-train closure"
            );
    }

    @Test
    void shouldPreserveReleaseHardeningBoundaries()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "release hardening and evidence closure, not a new product feature milestone",
                "Registration activation merely to satisfy a version number",
                "Login-limiter retuning without a verified defect",
                "Artifact signing, SLSA provenance, or reproducible-build guarantees",
                "Regulatory certification, production certification, or real-money operation"
            );

        assertThat(Files.readString(README))
            .contains(
                "registration `DEFER`",
                "password-login limiter",
                "simulated-money",
                "fail-closed",
                "credential-redaction"
            );
    }

    @Test
    void shouldPreserveDevelopmentHistoryWhileOpeningReleasePreparation()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [1.0.0] - 2026-08-30",
                "Advanced the active release-candidate development version to `1.0.0-SNAPSHOT`",
                "issue #189",
                "development-start checkpoint #190",
                "Prepared the exact `1.0.0` release candidate through issue #203",
                "## [0.16.0] - 2026-08-24",
                "[0.16.0]: https://github.com/nursena-pc/payflow/compare/v0.15.0...v0.16.0",
                "[1.0.0]: https://github.com/nursena-pc/payflow/compare/v0.16.0...v1.0.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v1.0.0...HEAD"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Open the Maven development line at `1.0.0-SNAPSHOT` through a protected PR",
                "published merge and tag commit: `da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13`",
                "annotated tag object: `8308e190960525924a550dafc8dcfcf61d4250d0`",
                "release workflow run: [`32757038003`]",
                "GitHub Release ID: `375880233`",
                "published JAR size: `100566879` bytes",
                "published JAR SHA-256: `8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6`"
            );
    }
}
