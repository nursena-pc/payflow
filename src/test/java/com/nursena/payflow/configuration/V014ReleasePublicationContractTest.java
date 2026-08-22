package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V014ReleasePublicationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of("docs", "releases", "v0.14.0.md");

    private static final Path RELEASE_WORKFLOW =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    @Test
    void shouldExposePublishedReleaseStatus()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "The immutable v0.14.0 publication record remains anchored",
                "## v0.14.0 release",
                "annotated tag `v0.14.0`",
                "d65929b98bb66b22f208d26f75a764e1ade78b6a",
                "31728977714",
                "100200050 bytes",
                "A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E"
            )
            .doesNotContain(
                "v0.14.0 release preparation",
                "The `0.14.0` release-preparation line"
            );
    }

    @Test
    void shouldFreezeVersionedReleaseMetadata()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.14.0] - 2026-08-12",
                "[0.14.0]: https://github.com/nursena-pc/payflow/compare/v0.13.0...v0.14.0"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.14.0",
                "v0.13.0...v0.14.0",
                "TOTP",
                "rotation requires an exact purpose-bound step-up grant"
            )
            .doesNotContain("0.14.0-SNAPSHOT");
    }

    @Test
    void shouldRecordPublishedRoadmapEvidence()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.14.0 — Released: MFA and Step-Up Authentication",
                "- [x] Pass protected `build-and-test` and `docker-smoke` checks for every increment",
                "- [x] protected feature and release-preparation pull requests are merged",
                "- [x] the v0.14.0 tag, JAR, checksum, and GitHub Release are published",
                "release-preparation PR: `#147`",
                "release-candidate commit: `1fba2dacc239d8c43149cebdf192e3086be356c3`",
                "published merge and tag commit: `d65929b98bb66b22f208d26f75a764e1ade78b6a`",
                "annotated tag object: `826c77a724915c386c375c2cc227597ae0331dda`",
                "release workflow run: [`31728977714`]",
                "published at: `2026-08-13T18:13:33Z`",
                "published JAR size: `100200050` bytes",
                "published JAR SHA-256: `A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E`",
                "release checksum verification: passed"
            );
    }

    @Test
    void shouldRetainProtectedTagPublicationWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "Release tags cannot publish a snapshot version",
                "git merge-base --is-ancestor",
                "./mvnw -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
