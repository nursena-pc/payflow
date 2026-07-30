package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReleaseWorkflowContractTest {

    private static final Path WORKFLOW_PATH =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    private static final Path RELEASE_NOTES_PATH =
        Path.of(
            "docs",
            "releases",
            "v0.9.0.md"
        );

    @Test
    void shouldDefineVerifiedTagReleaseWorkflow()
        throws IOException {

        String workflow =
            Files.readString(WORKFLOW_PATH);

        assertThat(workflow)
            .contains(
                "name: Release",
                "tags:",
                "'v[0-9]+.[0-9]+.[0-9]+'",
                "permissions:",
                "contents: write",
                "actions/checkout@v6",
                "actions/setup-java@v5",
                "mvn -B -ntp clean verify",
                "sha256sum",
                "actions/upload-artifact@v7",
                "gh release create",
                "--verify-tag",
                "docs/releases/${TAG}.md"
            )
            .doesNotContain(
                "pull_request_target",
                "secrets.GITHUB_TOKEN",
                "curl ",
                "wget "
            );
    }

    @Test
    void shouldProvideVersionedReleaseNotesAndAssets()
        throws IOException {

        String releaseNotes =
            Files.readString(RELEASE_NOTES_PATH);

        assertThat(releaseNotes)
            .contains(
                "# PayFlow v0.9.0",
                "Refresh-session security",
                "Redis-backed login protection",
                "880 tests",
                "payflow-0.9.0.jar",
                "payflow-0.9.0.jar.sha256",
                "`v0.8.0...v0.9.0`"
            )
            .doesNotContain(
                "0.9.0-SNAPSHOT",
                "real money processing"
            );
    }
}
