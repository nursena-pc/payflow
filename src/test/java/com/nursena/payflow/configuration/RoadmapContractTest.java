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
                "- [x] Open the v0.10.0 implementation issue",
                "- [x] Define trusted-proxy CIDR configuration",
                "- [x] Validate IPv4 and IPv6 network ranges at startup",
                "- [x] Bound accepted header length and proxy-hop count",
                "- [x] Define forwarding-header precedence explicitly",
                "- [x] Document direct-peer fallback and failure behavior",
                "- [x] Keep servlet and header parsing inside the inbound adapter",
                "- [x] Ignore forwarding headers when the direct peer is not trusted",
                "- [x] Parse trusted proxy chains from right to left",
                "- [x] Select the first untrusted address as the effective client",
                "- [x] Normalize IPv4 and IPv6 literals without DNS resolution",
                "- [x] Reject or safely fall back on malformed and obfuscated identifiers",
                "- [x] Verify spoofed forwarding headers are ignored from untrusted peers",
                "- [x] Verify a single trusted proxy",
                "- [x] Verify multi-hop trusted and untrusted proxy chains",
                "- [x] Verify IPv4, IPv6, and mixed-address chains",
                "- [x] Verify malformed, oversized, and excessive-hop inputs",
                "- [x] Verify direct-peer fallback",
                "- [x] Introduce an application-facing client-context abstraction",
                "- [x] Replace direct `HttpServletRequest#getRemoteAddr` coupling",
                "- [x] Feed the resolved effective client into the existing rate-limit port",
                "- [x] Preserve identity-counter and client-counter semantics",
                "- [x] Preserve generic `401`, stable `429`, and fail-closed `503` contracts",
                "- [x] Verify login rate limiting groups requests by effective client",
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
