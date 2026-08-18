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

    private static final Path RELEASE_NOTES =
        Path.of("docs", "releases", "v0.15.0.md");

    private static final Path RELEASE_WORKFLOW =
        Path.of(".github", "workflows", "release.yml");

    @Test
    void shouldExposeV015ReleasePreparationStatus()
        throws IOException {

        assertThat(Files.readString(POM))
            .contains("<version>0.15.0</version>")
            .doesNotContain("0.15.0-SNAPSHOT");

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.14.0 is the latest published release",
                "v0.15.0 is in protected release preparation",
                "Maven version frozen at `0.15.0`",
                "## v0.15.0 release preparation",
                "generalized abuse protection",
                "reproducible load and performance evidence",
                "operational dashboards and alerts",
                "issue #166"
            )
            .doesNotContain(
                "0.15.0-SNAPSHOT",
                "## v0.15.0 active development"
            );
    }

    @Test
    void shouldFreezeVersionedChangelogAndReleaseNotes()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.15.0] - 2026-08-18",
                "Prepared the v0.15.0 release candidate at Maven version `0.15.0`",
                "issue #149",
                "issue #166",
                "[0.15.0]: https://github.com/nursena-pc/payflow/compare/v0.14.0...v0.15.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.15.0...HEAD"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.15.0",
                "five bounded workflow identifiers",
                "Shared Redis enforcement",
                "Protected workflows",
                "Observability and operations",
                "Performance evidence",
                "Registration decision",
                "1,582 Maven tests",
                "payflow-0.15.0.jar",
                "payflow-0.15.0.jar.sha256",
                "`v0.14.0...v0.15.0`"
            )
            .doesNotContain("0.15.0-SNAPSHOT");
    }

    @Test
    void shouldPreserveSecurityAndDeferredRegistrationBoundaries()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "empty `202 Accepted` response",
                "`MFA_SECURITY_UNAVAILABLE`",
                "separate compatibility contract",
                "evidence-backed `DEFER` decision",
                "`201` / `400` / `409` public contract is unchanged",
                "not production-capacity certification",
                "schema remains at V24"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.15.0 — Release Preparation: Generalized Abuse Protection and Performance Evidence",
                "release finalization is tracked by issue `#166`",
                "- [x] Align OpenAPI, Postman, README, changelog, ADRs, threat model, and operations guidance",
                "- [x] Add focused unit, Redis, HTTP, concurrency, redaction, and dependency-failure tests",
                "- [x] Run the complete Maven verification suite and production Docker smoke on the exact release-candidate content",
                "- [ ] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head",
                "- [ ] Record immutable publication evidence after protected merge and publication"
            );
    }

    @Test
    void shouldRequireImmutablePublicationFromProtectedMainHistory()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "fetch-depth: 0",
                "Release tags cannot publish a snapshot version",
                "git fetch --no-tags origin main:refs/remotes/origin/main",
                "git merge-base --is-ancestor",
                "mvn -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "protected `CI` and `Docker Smoke` must still pass",
                "immutable tag, merge SHA, release workflow run, published artifact size, and published SHA-256 are recorded only after publication"
            );
    }
}