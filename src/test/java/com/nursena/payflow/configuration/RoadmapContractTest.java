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
    void shouldAlignRoadmapWithPublishedReleaseVersion()
        throws Exception {

        String projectVersion = readProjectVersion();
        String roadmap = Files.readString(ROADMAP);

        assertThat(projectVersion)
            .isEqualTo("0.13.0");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.13.0 is the latest tagged release",
                "the Maven version",
                "`" + projectVersion + "`",
                "The next planned milestone is v0.14.0",
                "## v0.10.0 — Released: Trusted Client Context",
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "## v0.12.0 — Released: JWT Signing-Key Rotation",
                "## v0.13.0 — Released: Account Recovery and Secure Mail Delivery",
                "726f631a0de800870813ccb0c00b2676eb5d172b",
                "31115952987"
            )
            .doesNotContain(
                "0.13.0-SNAPSHOT",
                "## v0.13.0 — Active Development",
                "## v0.13.0 — Release Candidate"
            );
    }

    @Test
    void shouldFreezeDeliveredScopeAndExplicitDeferrals()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Backfill every pre-v0.13.0 user as verified to prevent migration lockout",
                "- [x] Persist only fixed-length SHA-256 digests, never plaintext credentials",
                "- [x] Add generic `POST /api/v1/auth/email-verification/requests`",
                "- [x] Add token-confirmation `POST /api/v1/auth/password-recovery/confirm`",
                "- [x] Revoke all active refresh-token families with `PASSWORD_RECOVERY`",
                "- [x] Protect provider-ready mail bodies with AES-256-GCM before persistence",
                "- [x] Claim work with PostgreSQL leases and `FOR UPDATE SKIP LOCKED`",
                "- [x] Run the complete Maven verification suite and production Docker smoke",
                "1,174 tests, zero failures, zero errors",
                "Deferred to the generalized abuse-protection milestone",
                "They are not claimed by this release",
                "- [x] the protected v0.13.0 release-preparation pull request is merged",
                "- [x] the v0.13.0 tag, JAR, checksum, and GitHub Release are published",
                "9879780a418d8490b835c36b7a01cd0019621a7e",
                "78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA",
                "4FDD37BC1BF5D058A391A23784CCF87DED3FADCC3F9DB564806A8A52DC1F7B51"
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
