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

    private static final Path POM_PATH =
        Path.of("pom.xml");

    private static final Path ROADMAP_PATH =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldAlignRoadmapWithNextDevelopmentVersion()
        throws Exception {

        String projectVersion =
            readProjectVersion();

        String roadmap =
            Files.readString(ROADMAP_PATH);

        assertThat(projectVersion)
            .isEqualTo("0.10.0-SNAPSHOT");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.9.0 is the latest tagged release",
                "`" + projectVersion + "`",
                "## v0.9.0 — Released",
                "## v0.10.0 — Trusted Client Context",
                "- [ ] Define trusted-proxy CIDR configuration",
                "- [ ] Ignore forwarding headers when the direct peer is not trusted",
                "- [ ] Parse trusted proxy chains from right to left",
                "- [ ] Verify spoofed forwarding headers are ignored from untrusted peers",
                "- [ ] only configured proxy networks may influence effective client identity"
            )
            .doesNotContain(
                "0.9.0-SNAPSHOT",
                "The v0.9.0 release candidate",
                "## v0.7.0 — Identity and Session Security"
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
