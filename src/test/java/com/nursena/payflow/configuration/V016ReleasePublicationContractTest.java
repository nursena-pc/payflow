package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V016ReleasePublicationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of("docs", "releases", "v0.16.0.md");

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
                "PayFlow v0.16.0 is the latest published release",
                "annotated tag `v0.16.0`",
                "8308e190960525924a550dafc8dcfcf61d4250d0",
                "da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13",
                "32757038003",
                "375880233",
                "2026-08-24T17:40:22Z",
                "100566879 bytes",
                "8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6",
                "b14f5ea137012e7aa8557fa21c1c9fece151deb2447a0b636da7ee3a173d14b0"
            )
            .doesNotContain(
                "PayFlow v0.15.0 remains the latest published release",
                "reviewed v0.16.0 release candidate uses Maven version",
                "No v0.16.0 tag or GitHub Release has been published yet"
            );
    }

    @Test
    void shouldFreezeVersionedReleaseMetadata()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.16.0] - 2026-08-24",
                "[0.16.0]: https://github.com/nursena-pc/payflow/compare/v0.15.0...v0.16.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.16.0...HEAD",
                "32757038003",
                "375880233",
                "100566879",
                "8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.16.0",
                "registration remains under the evidence-backed `DEFER` decision",
                "`v0.15.0...v0.16.0`"
            )
            .doesNotContain("0.16.0-SNAPSHOT");
    }

    @Test
    void shouldRecordPublishedRoadmapEvidence()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.16.0 — Released: Stabilization, Recovery Rehearsals, and API Freeze",
                "- [x] Publish the annotated `v0.16.0` tag from the exact approved merge commit",
                "- [x] Publish and independently verify the executable JAR and SHA-256 checksum",
                "- [x] Publish the GitHub Release from reviewed versioned release notes",
                "- [x] Record immutable publication values only after publication",
                "release-finalization PR: `#187`",
                "reviewed release-candidate commit: `55694be7b76d122da10e52ddb1eab0de2fe48068`",
                "published merge and tag commit: `da8cefa9772d8e009b5ef1e5ab53d03bc44b1c13`",
                "annotated tag object: `8308e190960525924a550dafc8dcfcf61d4250d0`",
                "release workflow run: [`32757038003`]",
                "release workflow number: `9`",
                "2026-08-24T17:40:22Z",
                "GitHub Release ID: `375880233`",
                "published JAR size: `100566879` bytes",
                "published JAR SHA-256: `8c542fc6928179345e5cda3d0f66d1481f7277a88096a52a69952ed95f2958e6`",
                "checksum asset SHA-256: `b14f5ea137012e7aa8557fa21c1c9fece151deb2447a0b636da7ee3a173d14b0`",
                "release checksum verification: passed",
                "published release notes verification: exact match"
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