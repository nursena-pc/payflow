package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015ReleasePublicationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of("docs", "releases", "v0.15.0.md");

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
                "PayFlow v0.15.0 is the latest published release",
                "## v0.15.0 release",
                "annotated tag `v0.15.0`",
                "c29a067ca3a64514444e17db59a2b862d26f5950",
                "32172653513",
                "100236578 bytes",
                "7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080"
            )
            .doesNotContain(
                "v0.15.0 is in protected release preparation",
                "## v0.15.0 release preparation"
            );
    }

    @Test
    void shouldFreezeVersionedReleaseMetadata()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.15.0] - 2026-08-18",
                "[0.15.0]: https://github.com/nursena-pc/payflow/compare/v0.14.0...v0.15.0"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.15.0",
                "Registration remains deliberately unwired",
                "v0.14.0...v0.15.0"
            )
            .doesNotContain("0.15.0-SNAPSHOT");
    }

    @Test
    void shouldRecordPublishedRoadmapEvidence()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.15.0 — Released: Generalized Abuse Protection and Performance Evidence",
                "- [x] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head",
                "- [x] Record immutable publication evidence after protected merge and publication",
                "- [x] protected feature and release-preparation pull requests are merged",
                "- [x] the v0.15.0 tag, JAR, checksum, and GitHub Release are published",
                "release-preparation PR: `#167`",
                "release-candidate commit: `2f334ca28c78533e5bfc3a2dc5ee3c4a3d903976`",
                "published merge and tag commit: `c29a067ca3a64514444e17db59a2b862d26f5950`",
                "annotated tag object: `a1aa528b4933c69a3fa81c10a103154bd1d6a327`",
                "32172653513",
                "2026-08-18T18:52:43Z",
                "9338113318",
                "372572363",
                "100236578",
                "7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080"
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