package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V013ReleasePreparationContractTest {

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
    void shouldExposeProtectedReleasePreparationStatus()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.12.0 is the latest published release",
                "v0.13.0 is in protected release preparation",
                "Maven version frozen at `0.13.0`",
                "## v0.13.0 release preparation",
                "1,174 tests with zero failures and zero errors",
                "Per-identity and per-client Redis quotas",
                "explicitly deferred"
            )
            .doesNotContain(
                "0.13.0-SNAPSHOT",
                "## v0.13.0 active development"
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
                "[0.13.0]: https://github.com/nursena-pc/payflow/compare/v0.12.0...v0.13.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.13.0...HEAD"
            );

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.13.0",
                "Email ownership verification",
                "Password recovery",
                "Secure transactional mail outbox",
                "1,174 tests",
                "payflow-0.13.0.jar",
                "payflow-0.13.0.jar.sha256",
                "`v0.12.0...v0.13.0`"
            )
            .doesNotContain(
                "0.13.0-SNAPSHOT",
                "real money processing"
            );
    }

    @Test
    void shouldRecordSecurityAndUpgradeBoundaries()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "digest-only persistence",
                "single-use consumption",
                "PASSWORD_RECOVERY",
                "AES-256-GCM",
                "FOR UPDATE SKIP LOCKED",
                "bounded duplicate risk",
                "Flyway V15",
                "Flyway V16",
                "Flyway V17",
                "purpose-specific Redis request quotas are deferred"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "They are not claimed by this release candidate",
                "purpose-specific Redis request quotas"
            );
    }

    @Test
    void shouldRequireFinalTagFromMainAndNonSnapshotMetadata()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "fetch-depth: 0",
                "Release tags cannot publish a snapshot version",
                "git fetch --no-tags origin main:refs/remotes/origin/main",
                "git merge-base --is-ancestor",
                "Tag commit ${GITHUB_SHA} is not reachable from origin/main",
                "mvn -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
