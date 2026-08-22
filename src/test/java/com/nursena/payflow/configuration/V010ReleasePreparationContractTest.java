package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class V010ReleasePreparationContractTest {

    private static final Path ROADMAP_PATH =
        Path.of(
            "docs",
            "roadmap.md"
        );

    private static final Path RELEASE_NOTES_PATH =
        Path.of(
            "docs",
            "releases",
            "v0.10.0.md"
        );

    private static final Path WORKFLOW_DIRECTORY =
        Path.of(
            ".github",
            "workflows"
        );

    @Test
    void shouldRetainPublishedReleaseRecord()
        throws IOException {

        assertThat(Files.exists(RELEASE_NOTES_PATH))
            .isTrue();

        assertThat(
            Files.readString(ROADMAP_PATH)
        )
            .contains(
                "## v0.10.0 — Released: Trusted Client Context"
            )
            .doesNotContain(
                "The v0.10.0 release candidate uses"
            );
    }

    @Test
    void shouldDocumentReleaseScopeAndAssets()
        throws IOException {

        assertThat(
            Files.readString(RELEASE_NOTES_PATH)
        )
            .contains(
                "# PayFlow v0.10.0",
                "## Highlights",
                "### Effective client-address resolution",
                "### Login-protection integration",
                "### Bounded observability",
                "## Upgrade notes",
                "## Release assets",
                "`payflow-0.10.0.jar`",
                "`payflow-0.10.0.jar.sha256`",
                "`v0.9.0...v0.10.0`"
            );
    }

    @Test
    void shouldRecordCompletedPublicationCriteria()
        throws IOException {

        String roadmap =
            Files.readString(ROADMAP_PATH);

        String releaseNotes =
            Files.readString(RELEASE_NOTES_PATH);

        assertThat(roadmap)
            .contains(
                "- [x] Merge v0.10.0 release preparation through protected PR #104",
                "- [x] Tag merge commit `9dad6bdf0b8d1e166ba6454a6d791561cc30b671` as `v0.10.0`",
                "- [x] Publish `payflow-0.10.0.jar`",
                "- [x] Publish and verify `payflow-0.10.0.jar.sha256`",
                "- [x] Publish the GitHub Release",
                "- [x] the release-preparation pull request is merged",
                "- [x] the v0.10.0 tag is published",
                "- [x] the executable JAR and SHA-256 checksum are published",
                "- [x] the GitHub Release is published"
            );

        assertThat(releaseNotes)
            .contains(
                "## Publication verification",
                "release-preparation PR: `#104`",
                "release workflow run: `30675532483`",
                "verified SHA-256: `174D7F51D27F19B0A45B281869FF86BD9DC52F59B41B20B479827B92102D957B`"
            );
    }

    @Test
    void shouldHaveTagTriggeredReleaseWorkflow()
        throws IOException {

        List<String> workflows;

        try (Stream<Path> paths =
            Files.list(WORKFLOW_DIRECTORY)) {

            workflows =
                paths
                    .filter(Files::isRegularFile)
                    .map(
                        V010ReleasePreparationContractTest
                            ::readUnchecked
                    )
                    .toList();
        }

        assertThat(workflows)
            .anySatisfy(
                workflow ->
                    assertThat(workflow)
                        .contains(
                            "name: Release",
                            "tags:",
                            "v[0-9]+.[0-9]+.[0-9]+",
                            "contents: write",
                            "./mvnw -B -ntp clean verify",
                            "sha256sum",
                            "actions/upload-artifact@v7",
                            "gh release create",
                            "--verify-tag"
                        )
            );
    }

    private static String readUnchecked(
        Path path
    ) {
        try {
            return Files.readString(path);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(
                exception
            );
        }
    }

}
