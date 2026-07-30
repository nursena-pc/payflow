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
    void shouldAlignRoadmapWithReleaseVersion()
        throws Exception {

        String projectVersion =
            readProjectVersion();

        String roadmap =
            Files.readString(ROADMAP_PATH);

        assertThat(projectVersion)
            .isEqualTo("0.9.0");

        assertThat(roadmap)
            .contains(
                "Maven version `" + projectVersion + "`",
                "## v0.9.0 — Redis-Backed Login Protection",
                "- [x] Verify identity and client thresholds with real Redis",
                "- [x] Verify HTTP `429` and `Retry-After`",
                "- [x] Verify Redis outage produces fail-closed HTTP `503`",
                "- [x] Open the pull request linked to issue #98",
                "- [x] Merge through the protected pull-request workflow",
                "- [x] Prepare v0.9.0 release notes",
                "- [ ] Tag the verified release commit as `v0.9.0`",
                "- [ ] the GitHub Release is published"
            )
            .doesNotContain(
                "0.9.0-SNAPSHOT",
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
