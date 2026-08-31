package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100ReleasePublicationContractTest {

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
    void shouldExposePublishedV100ReleaseStatus()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v1.0.0 is the latest tagged and published release",
                "The immutable v1.0.0 publication record is anchored to annotated tag `v1.0.0`",
                "bc1750b17bbdfdcc24764c943afbfd9943e33190",
                "1adf0b38f1d2da82097c58e41aea4e36a2b4a643",
                "33388085847",
                "379712093",
                "2026-08-31T11:50:28Z",
                "100566872 bytes",
                "ed58f5e812e6dfd3ee1ced8f480265bbc6221ff5de760253095762294a9dd8b1",
                "24d66af06c41b1c5bad37c218c8a20678b7fe85d7cfb717ae28a8916cfd79a5c",
                "published release notes exactly match the reviewed versioned notes",
                "issue #208",
                "## v1.0.0 release"
            )
            .doesNotContain(
                "PayFlow v0.16.0 remains the latest published release",
                "reviewed v1.0.0 release-preparation candidate uses Maven version `1.0.0`",
                "No `v1.0.0` tag or GitHub Release has been published yet",
                "## v1.0.0 release preparation"
            );
    }

    @Test
    void shouldFreezeV100PublicationMetadataInChangelog()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [1.0.0] - 2026-08-31",
                "Published annotated `v1.0.0` through issue #207",
                "1adf0b38f1d2da82097c58e41aea4e36a2b4a643",
                "bc1750b17bbdfdcc24764c943afbfd9943e33190",
                "33388085847",
                "379712093",
                "2026-08-31T11:50:28Z",
                "100566872-byte `payflow-1.0.0.jar`",
                "ed58f5e812e6dfd3ee1ced8f480265bbc6221ff5de760253095762294a9dd8b1",
                "24d66af06c41b1c5bad37c218c8a20678b7fe85d7cfb717ae28a8916cfd79a5c",
                "published release notes exactly match `docs/releases/v1.0.0.md`",
                "issue #208",
                "[1.0.0]: https://github.com/nursena-pc/payflow/compare/v0.16.0...v1.0.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v1.0.0...HEAD"
            );
    }

    @Test
    void shouldRecordImmutableV100RoadmapEvidence()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v1.0.0 — Released: Release Hardening and Evidence Closure",
                "Immutable-publication issue: [#207]",
                "Publication-record issue: [#208]",
                "- [x] Publish annotated tag `v1.0.0` only from the exact approved release merge",
                "- [x] Require the tag-triggered Release workflow to succeed",
                "- [x] Independently download and verify the published executable JAR and checksum",
                "- [x] Verify published release notes exactly match the reviewed versioned release notes",
                "- [x] Record real tag object, tag target, merge SHA, workflow run, release ID, artifact size, and SHA-256 only after publication",
                "approved release merge and tag target: `1adf0b38f1d2da82097c58e41aea4e36a2b4a643`",
                "annotated tag object: `bc1750b17bbdfdcc24764c943afbfd9943e33190`",
                "release workflow run: [`33388085847`]",
                "release workflow number: `10`",
                "2026-08-31T11:50:28Z",
                "workflow artifact ID: `9756624045`",
                "GitHub Release ID: `379712093`",
                "JAR asset ID: `537914002`",
                "published JAR size: `100566872` bytes",
                "published JAR SHA-256: `ed58f5e812e6dfd3ee1ced8f480265bbc6221ff5de760253095762294a9dd8b1`",
                "checksum asset ID: `537914001`",
                "checksum asset size: `84` bytes",
                "checksum asset SHA-256: `24d66af06c41b1c5bad37c218c8a20678b7fe85d7cfb717ae28a8916cfd79a5c`",
                "release checksum verification: passed",
                "published release notes verification: exact match",
                "tag mutation after publication: none"
            );
    }

    @Test
    void shouldPreserveReviewedNotesAndProtectedPublicationWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v1.0.0",
                "intentionally not recorded here before immutable publication",
                "`payflow-1.0.0.jar`",
                "`payflow-1.0.0.jar.sha256`"
            )
            .doesNotContain(
                "1.0.0-SNAPSHOT"
            );

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "Release tags cannot publish a snapshot version",
                "git merge-base --is-ancestor",
                "Tag ${TAG} does not match Maven version ${VERSION}",
                "./mvnw -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
