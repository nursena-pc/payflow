package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V016DevelopmentContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldOpenV016DevelopmentLine()
        throws IOException {

        assertThat(Files.readString(POM))
            .contains(
                "<version>0.16.0-SNAPSHOT</version>"
            );

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.15.0 is the latest published release",
                "the active development line uses `0.16.0-SNAPSHOT`",
                "## v0.16.0 active development",
                "issue #169",
                "/api/v1",
                "PostgreSQL backup/restore",
                "Flyway migration rehearsals",
                "SBOM/provenance evidence",
                "clean-environment release rehearsal"
            );
    }

    @Test
    void shouldDefineIncrementalStabilizationPlan()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.16.0 — Active Development: Stabilization, Recovery Rehearsals, and API Freeze",
                "Tracking issue: [#169]",
                "### Increment 1 — Stabilization baseline and compatibility contract",
                "### Increment 2 — PostgreSQL backup and restore rehearsal",
                "### Increment 3 — Flyway clean-install and upgrade rehearsal",
                "### Increment 4 — Redis and Kafka outage/recovery operations",
                "### Increment 5 — API, OpenAPI, Postman, and documentation drift review",
                "### Increment 6 — Dependency and supply-chain evidence",
                "### Increment 7 — Clean-environment release rehearsal",
                "### Increment 8 — v0.16.0 finalization and publication evidence"
            );
    }

    @Test
    void shouldFreezeFeatureAndCompatibilityBoundaries()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "Define the v1 compatibility boundary so existing `/api/v1` request, response, and error semantics cannot change silently",
                "Freeze existing security, privacy, fail-closed, simulated-money, and modular-monolith boundaries",
                "Document recovery and rollback boundaries without claiming unsupported down-migrations",
                "Preserve existing fail-closed security behavior during dependency failure",
                "Preserve the evidence-backed registration `DEFER` decision unless new evidence and a separately reviewed change justify activation",
                "New wallet, transfer, payment, or transaction-history features",
                "New abuse-protection algorithms, quota retuning, or generalized registration activation",
                "v1.0.0 publication itself"
            );
    }

    @Test
    void shouldRecordDevelopmentTransitionWithoutRewritingV015Publication()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "Advanced the active development version to `0.16.0-SNAPSHOT`",
                "issue #169",
                "## [0.15.0] - 2026-08-18",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.15.0...HEAD"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "published merge and tag commit: `c29a067ca3a64514444e17db59a2b862d26f5950`",
                "annotated tag object: `a1aa528b4933c69a3fa81c10a103154bd1d6a327`",
                "release workflow run: [`32172653513`]",
                "published JAR size: `100236578` bytes",
                "published JAR SHA-256: `7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080`"
            );
    }
}