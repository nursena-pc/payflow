package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class V011ReleasePreparationContractTest {

    private static final Path POM_PATH =
        Path.of("pom.xml");

    private static final Path README_PATH =
        Path.of("README.md");

    private static final Path ROADMAP_PATH =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES_PATH =
        Path.of(
            "docs",
            "releases",
            "v0.11.0.md"
        );

    private static final Path RELEASE_WORKFLOW_PATH =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    private static final Path GIT_ATTRIBUTES_PATH =
        Path.of(".gitattributes");

    @Test
    void shouldUseReleaseCandidateVersion()
        throws Exception {

        assertThat(readProjectVersion())
            .isEqualTo("0.11.0");

        assertThat(
            Files.readString(README_PATH)
        )
            .contains(
                "v0.11.0 is in protected release preparation",
                "docs/releases/v0.11.0.md",
                "v0.11.0 release preparation",
                "verified merge commit of the release-preparation pull request"
            );

        assertThat(
            Files.readString(ROADMAP_PATH)
        )
            .contains(
                "The v0.11.0 release candidate uses",
                "the Maven version `0.11.0`",
                "release train #106",
                "## v0.11.0 — Release Candidate: Structured Logging and Request Correlation"
            )
            .doesNotContain(
                "0.11.0-SNAPSHOT"
            );
    }

    @Test
    void shouldDocumentReleaseScopeVerificationAndAssets()
        throws IOException {

        assertThat(
            Files.readString(RELEASE_NOTES_PATH)
        )
            .contains(
                "# PayFlow v0.11.0",
                "### Trustworthy request correlation",
                "### Structured JSON logging",
                "### Bounded request-completion events",
                "### Security boundaries",
                "## Verification",
                "1,017 complete Maven tests passed",
                "215 Surefire reports were produced",
                "## Upgrade notes",
                "No database migration is included.",
                "## Release assets",
                "`payflow-0.11.0.jar`",
                "`payflow-0.11.0.jar.sha256`",
                "`v0.10.0...v0.11.0`"
            );
    }

    @Test
    void shouldKeepPublicationCriteriaOpenUntilProtectedReleaseCompletes()
        throws IOException {

        assertThat(
            Files.readString(ROADMAP_PATH)
        )
            .contains(
                "- [x] Merge the observability increment through protected PR #108",
                "- [x] Prepare v0.11.0 release notes",
                "- [ ] Merge v0.11.0 release preparation through a protected pull request",
                "- [ ] Tag the verified release merge commit as `v0.11.0`",
                "- [ ] Publish `payflow-0.11.0.jar`",
                "- [ ] Publish and independently verify `payflow-0.11.0.jar.sha256`",
                "- [ ] Publish the GitHub Release"
            );
    }

    @Test
    void shouldRetainTagTriggeredVerifiedReleaseWorkflow()
        throws IOException {

        assertThat(
            Files.readString(RELEASE_WORKFLOW_PATH)
        )
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
            );
    }

    @Test
    void shouldEnforceRepositoryLineEndingPolicy()
        throws IOException {

        assertThat(
            Files.readString(GIT_ATTRIBUTES_PATH)
        )
            .contains(
                "* text=auto",
                "*.java       text eol=lf",
                "*.xml        text eol=lf",
                "*.md         text eol=lf",
                "*.cmd        text eol=crlf",
                "*.ps1        text eol=crlf"
            );
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
