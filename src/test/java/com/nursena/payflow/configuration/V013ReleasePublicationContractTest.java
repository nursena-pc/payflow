package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V013ReleasePublicationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of(
            "docs",
            "releases",
            "v0.13.0.md"
        );

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
                "The immutable v0.13.0 publication record remains anchored",
                "## v0.13.0 release",
                "annotated tag `v0.13.0`",
                "726f631a0de800870813ccb0c00b2676eb5d172b",
                "31115952987",
                "100015861 bytes",
                "78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA"
            )
            .doesNotContain(
                "v0.13.0 is in protected release preparation",
                "## v0.13.0 release preparation"
            );
    }

    @Test
    void shouldFreezeVersionedChangelogAndReleaseNotes()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.13.0] - 2026-08-06",
                "[#120]",
                "[#121]",
                "[#122]",
                "[#123]",
                "[#124]",
                "[#125]",
                "[0.13.0]: https://github.com/nursena-pc/payflow/compare/v0.12.0...v0.13.0"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.13.0",
                "## Publication record",
                "[#126]",
                "[#127]",
                "9879780a418d8490b835c36b7a01cd0019621a7e",
                "726f631a0de800870813ccb0c00b2676eb5d172b",
                "run 31115952987",
                "2026-08-06T15:35:55Z",
                "100015861",
                "78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA",
                "4FDD37BC1BF5D058A391A23784CCF87DED3FADCC3F9DB564806A8A52DC1F7B51"
            )
            .doesNotContain(
                "0.13.0-SNAPSHOT",
                "real money processing"
            );
    }

    @Test
    void shouldRecordPublishedRoadmapEvidence()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.13.0 — Released: Account Recovery and Secure Mail Delivery",
                "- [x] the protected v0.13.0 release-preparation pull request is merged",
                "- [x] the v0.13.0 tag, JAR, checksum, and GitHub Release are published",
                "release-preparation PR: `#127`",
                "release-candidate commit: `2d4c8b9b30b2291108da93b0df1edab97f032328`",
                "published merge and tag commit: `726f631a0de800870813ccb0c00b2676eb5d172b`",
                "annotated tag object: `9879780a418d8490b835c36b7a01cd0019621a7e`",
                "published JAR size: `100015861` bytes",
                "published JAR SHA-256: `78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA`",
                "publication-evidence JSON SHA-256: `4FDD37BC1BF5D058A391A23784CCF87DED3FADCC3F9DB564806A8A52DC1F7B51`"
            );
    }

    @Test
    void shouldRetainProtectedTagPublicationWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "fetch-depth: 0",
                "Release tags cannot publish a snapshot version",
                "git fetch --no-tags origin main:refs/remotes/origin/main",
                "git merge-base --is-ancestor",
                "Tag commit ${GITHUB_SHA} is not reachable from origin/main",
                "./mvnw -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
