package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015DevelopmentContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldOpenV015DevelopmentLine()
        throws IOException {

        assertThat(Files.readString(POM))
            .contains(
                "<version>0.15.0-SNAPSHOT</version>"
            );

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.14.0 is the latest published release",
                "the active development line uses `0.15.0-SNAPSHOT`",
                "## v0.15.0 active development",
                "generalized abuse protection",
                "reproducible load and performance evidence",
                "operational dashboards and alerts",
                "issue #149"
            );
    }

    @Test
    void shouldDefineIncrementalDeliveryPlan()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.15.0 — Active Development: Generalized Abuse Protection and Performance Evidence",
                "Tracking issue: [#149]",
                "### Increment 1 — Threat model, policy contract, and configuration",
                "### Increment 2 — Shared Redis enforcement foundation",
                "### Increment 3 — Account-action request protection",
                "### Increment 4 — MFA challenge and step-up protection",
                "### Increment 5 — Metrics, dashboards, alerts, and operations",
                "### Increment 6 — Reproducible load and performance evidence",
                "### Increment 7 — Contract alignment and release preparation"
            );
    }

    @Test
    void shouldFreezeSecurityAndPrivacyBoundaries()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "application-facing abuse-protection policy independent from controllers and servlet APIs",
                "trusted effective-client-address boundary",
                "atomic Redis decisions with explicit expiration and bounded key cardinality",
                "Preserve generic public responses and anti-enumeration behavior",
                "## Explicit v0.15.0 non-goals",
                "Active-authenticator replacement",
                "CAPTCHA or third-party bot-detection services",
                "Adaptive machine-learning risk scoring"
            );
    }

    @Test
    void shouldAdvanceUnreleasedMetadata()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "Advanced the active development version to `0.15.0-SNAPSHOT`",
                "issue #149",
                "[0.14.0]: https://github.com/nursena-pc/payflow/compare/v0.13.0...v0.14.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.14.0...HEAD"
            );
    }
}
