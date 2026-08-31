package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100ReleasePreparationContractTest {

    private static final Path POM =
        Path.of("pom.xml");

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

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of("docs", "releases", "v1.0.0.md");

    private static final Path RELEASE_WORKFLOW =
        Path.of(".github", "workflows", "release.yml");

    @Test
    void shouldFreezeExactV100ReleaseVersion()
        throws IOException {

        assertThat(Files.readString(POM))
            .contains(
                "<version>1.0.0</version>"
            )
            .doesNotContain(
                "<version>1.0.0-SNAPSHOT</version>"
            );

        assertThat(Files.readString(OPENAPI_CONFIGURATION))
            .contains(
                "API_VERSION",
                "\"1.0.0\""
            );
    }

    @Test
    void shouldRetainReviewedV100ReleasePreparationHistory()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [1.0.0] - 2026-08-31",
                "Prepared the exact `1.0.0` release candidate through issue #203",
                "[1.0.0]: https://github.com/nursena-pc/payflow/compare/v0.16.0...v1.0.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v1.0.0...HEAD"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "Release-preparation issue: [#203]",
                "### Checkpoint 7 — v1.0.0 release preparation",
                "- [x] Transition the reviewed candidate to exact version `1.0.0` only through a focused release-preparation PR",
                "- [x] Add reviewed `docs/releases/v1.0.0.md` from implemented and verified evidence only"
            );

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v1.0.0 is the latest tagged and published release",
                "## v1.0.0 release"
            )
            .doesNotContain(
                "PayFlow v0.16.0 remains the latest published release",
                "reviewed v1.0.0 release-preparation candidate uses Maven version `1.0.0`",
                "No `v1.0.0` tag or GitHub Release has been published yet"
            );
    }

    @Test
    void shouldRetainReviewedV100ReleaseNotesAsHistoricalPublicationInput()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v1.0.0",
                "complete clean-environment Maven suite passed `1649 / 0 / 0 / 0`",
                "frozen `/api/v1`: 30 canonical operations across 28 normalized paths",
                "OpenAPI version: `1.0.0`",
                "registration remains `DEFER`",
                "intentionally not recorded here before immutable publication",
                "`payflow-1.0.0.jar`",
                "`payflow-1.0.0.jar.sha256`"
            )
            .doesNotContain(
                "1.0.0-SNAPSHOT"
            );
    }

    @Test
    void shouldRetainProtectedTagPublicationWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "tags:",
                "'v[0-9]+.[0-9]+.[0-9]+'",
                "Release tags cannot publish a snapshot version",
                "git merge-base --is-ancestor",
                "Tag ${TAG} does not match Maven version ${VERSION}",
                "docs/releases/${TAG}.md",
                "./mvnw -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
