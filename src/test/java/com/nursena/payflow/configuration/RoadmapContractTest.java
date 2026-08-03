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

class RoadmapContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldAlignRoadmapWithActiveSnapshot()
        throws Exception {

        String projectVersion = readProjectVersion();
        String roadmap = Files.readString(ROADMAP);

        assertThat(projectVersion)
            .isEqualTo("0.12.0-SNAPSHOT");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.11.0 is the latest tagged release",
                "the Maven version `" + projectVersion + "`",
                "## v0.10.0 — Released: Trusted Client Context",
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "## v0.12.0 — Active Development: JWT Signing-Key Rotation",
                "JWT signing-key rotation increment",
                "stable `kid` issuance",
                "active and previous",
                "key-provider boundary",
                "fail-fast local key loading"
            )
            .doesNotContain(
                "The v0.11.0 release candidate uses",
                "v0.11.0 is in protected release preparation"
            );
    }

    @Test
    void shouldKeepV012ReleaseGatesOpen()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Open the dedicated v0.12.0 implementation issue #112",
                "- [ ] Pass the complete Maven verification suite",
                "- [ ] Pass protected `build-and-test` and Docker smoke CI",
                "- [ ] Prepare v0.12.0 release notes after the implementation PR is merged",
                "- [ ] Tag the verified release merge commit as `v0.12.0`",
                "- [ ] focused and complete Maven verification pass",
                "- [ ] v0.12.0 release preparation and publication gates complete"
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

        try (InputStream input = Files.newInputStream(POM)) {
            Element project = factory
                .newDocumentBuilder()
                .parse(input)
                .getDocumentElement();

            NodeList children = project.getChildNodes();

            for (
                int index = 0;
                index < children.getLength();
                index++
            ) {
                Node child = children.item(index);

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
