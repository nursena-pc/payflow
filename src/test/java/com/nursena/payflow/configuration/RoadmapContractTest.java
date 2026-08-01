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
    void shouldAlignRoadmapWithPublishedReleaseAndNextDevelopmentVersion()
        throws Exception {

        String projectVersion =
            readProjectVersion();

        String roadmap =
            Files.readString(ROADMAP_PATH);

        assertThat(projectVersion)
            .isEqualTo("0.11.0-SNAPSHOT");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.10.0 is the latest tagged release",
                "`" + projectVersion + "`",
                "## v0.9.0 — Released",
                "## v0.10.0 — Released: Trusted Client Context",
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
                "- [x] Add bounded decision metrics for source and fallback outcome",
                "- [x] Keep raw client addresses out of metric labels and logs",
                "- [x] Verify login rate limiting groups requests by effective client",
                "- [x] Run the complete Maven verification suite",
                "- [x] Add an ADR for the proxy trust model",
                "- [x] Update deployment and login-protection documentation",
                "- [x] Add reverse-proxy configuration examples",
                "- [x] Update architecture diagrams where the trust boundary is shown",
                "- [x] only configured proxy networks may influence effective client identity",
                "- [x] spoofed forwarding headers from untrusted peers are ignored",
                "- [x] trusted chains resolve deterministically for IPv4 and IPv6",
                "- [x] malformed or excessive forwarding input fails safely",
                "- [x] login rate limiting uses the effective client without changing public error contracts",
                "- [x] raw client addresses remain excluded from metric labels and logs",
                "- [x] focused unit, integration, and acceptance tests pass",
                "- [x] the complete Maven suite passes",
                "- [x] OpenAPI and operations documentation match the implementation",
                "- [x] Pass protected-branch CI and review checks",
                "- [x] Prepare v0.10.0 release notes",
                "- [x] Merge v0.10.0 release preparation through protected PR #104",
                "- [x] Tag merge commit `9dad6bdf0b8d1e166ba6454a6d791561cc30b671` as `v0.10.0`",
                "- [x] Publish `payflow-0.10.0.jar`",
                "- [x] Publish and verify `payflow-0.10.0.jar.sha256`",
                "- [x] Publish the GitHub Release",
                "- [x] protected-branch CI passes for the feature merge",
                "- [x] v0.10.0 release notes are prepared",
                "- [x] the release-preparation pull request is merged",
                "- [x] the v0.10.0 tag is published",
                "- [x] the executable JAR and SHA-256 checksum are published",
                "- [x] the GitHub Release is published",
                "release workflow run: `30675532483`",
                "verified SHA-256: `174D7F51D27F19B0A45B281869FF86BD9DC52F59B41B20B479827B92102D957B`"
            )
            .doesNotContain(
                "0.9.0-SNAPSHOT",
                "0.10.0-SNAPSHOT",
                "The v0.9.0 release candidate",
                "The v0.10.0 release candidate",
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
