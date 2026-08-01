package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class V010ReleasePreparationContractTest {

    private static final Path POM_PATH =
        Path.of("pom.xml");

    private static final Path README_PATH =
        Path.of("README.md");

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
    void shouldUseNextDevelopmentVersionAfterPublishedRelease()
        throws Exception {

        assertThat(readProjectVersion())
            .isEqualTo("0.11.0-SNAPSHOT");

        assertThat(
            Files.readString(README_PATH)
        )
            .contains(
                "v0.10.0 is the latest published release",
                "v0.11.0 development line",
                "docs/releases/v0.10.0.md"
            );

        assertThat(
            Files.readString(ROADMAP_PATH)
        )
            .contains(
                "PayFlow v0.10.0 is the latest tagged release",
                "`0.11.0-SNAPSHOT`",
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
                            "mvn -B -ntp clean verify",
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

    private static String readProjectVersion()
        throws Exception {

        DocumentBuilderFactory factory =
            DocumentBuilderFactory.newInstance();

        factory.setFeature(
            "http://apache.org/xml/features/disallow-doctype-decl",
            true
        );

        try (InputStream input =
            Files.newInputStream(POM_PATH)) {

            Element project =
                factory
                    .newDocumentBuilder()
                    .parse(input)
                    .getDocumentElement();

            NodeList children =
                project.getChildNodes();

            for (int index = 0;
                index < children.getLength();
                index++) {

                Node child =
                    children.item(index);

                if (
                    child.getNodeType()
                        == Node.ELEMENT_NODE
                        && "version".equals(
                            child.getNodeName()
                        )
                ) {
                    return child
                        .getTextContent()
                        .trim();
                }
            }
        }

        throw new IOException(
            "Project version was not found in pom.xml"
        );
    }
}
