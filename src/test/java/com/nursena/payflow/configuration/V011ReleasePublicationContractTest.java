package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V011ReleasePublicationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of(
            "docs",
            "releases",
            "v0.11.0.md"
        );

    private static final Path RELEASE_WORKFLOW =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    @Test
    void shouldRetainPublishedReleaseRecord()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "## v0.11.0 release",
                "docs/releases/v0.11.0.md",
                "00401d55546fb819fe7d96a8fad8e8c43e37649c"
            )
            .doesNotContain(
                "v0.11.0 is in protected release preparation"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "00401d55546fb819fe7d96a8fad8e8c43e37649c",
                "protected PR #111",
                "release workflow run: `30816366250`",
                "executable JAR size: `99,121,200` bytes",
                "AFA7836636F034BEA0CF8281851C1619E183B2AAEAC4F3C14D3FA39F40F7ABD0"
            );
    }

    @Test
    void shouldRetainReleaseScopeAndAssets()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.11.0",
                "### Trustworthy request correlation",
                "### Structured JSON logging",
                "### Bounded request-completion events",
                "### Security boundaries",
                "## Verification",
                "## Upgrade notes",
                "No database migration is included.",
                "## Release assets",
                "`payflow-0.11.0.jar`",
                "`payflow-0.11.0.jar.sha256`"
            );
    }

    @Test
    void shouldRecordCompletedPublicationCriteria()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Merge v0.11.0 release preparation through protected PR #111",
                "- [x] Pass 1,022 complete release-candidate tests with zero failures and zero errors",
                "- [x] Tag merge commit `00401d55546fb819fe7d96a8fad8e8c43e37649c` as `v0.11.0`",
                "- [x] Publish `payflow-0.11.0.jar`",
                "- [x] Publish and independently verify `payflow-0.11.0.jar.sha256`",
                "- [x] Publish the GitHub Release"
            );
    }

    @Test
    void shouldRetainVerifiedReleaseWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "name: Release",
                "v[0-9]+.[0-9]+.[0-9]+",
                "mvn -B -ntp clean verify",
                "sha256sum",
                "gh release create",
                "--verify-tag"
            );
    }
}
