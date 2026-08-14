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
    void shouldAlignRoadmapWithActiveDevelopmentVersion()
        throws Exception {

        String projectVersion = readProjectVersion();
        String roadmap = Files.readString(ROADMAP);
        String normalizedRoadmap = normalizeWhitespace(roadmap);

        assertThat(projectVersion)
            .isEqualTo("0.15.0-SNAPSHOT");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.14.0 is the latest tagged release",
                "The Maven version is `" + projectVersion + "`",
                "## v0.10.0 — Released: Trusted Client Context",
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "## v0.12.0 — Released: JWT Signing-Key Rotation",
                "## v0.13.0 — Released: Account Recovery and Secure Mail Delivery",
                "## v0.14.0 — Released: MFA and Step-Up Authentication",
                "## v0.15.0 — Active Development: Generalized Abuse Protection and Performance Evidence",
                "d65929b98bb66b22f208d26f75a764e1ade78b6a",
                "31728977714"
            )
            .doesNotContain(
                "0.14.0-SNAPSHOT",
                "## v0.14.0 — Release Candidate",
                "## v0.14.0 — Release Preparation"
            );

        assertThat(normalizedRoadmap)
            .contains(
                "TOTP multi-factor authentication",
                "digest-only recovery codes",
                "bounded step-up authentication",
                "generalized abuse protection",
                "load and performance evidence",
                "operational dashboards and alerts"
            );
    }

    @Test
    void shouldFreezeV013AndV014PublicationBoundaries()
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
                "4FDD37BC1BF5D058A391A23784CCF87DED3FADCC3F9DB564806A8A52DC1F7B51",
                "- [x] Open the dedicated v0.14.0 implementation issue",
                "Keep MFA state separate from `UserStatus` and email-verification state",
                "Protect every pending or active TOTP secret before PostgreSQL persistence",
                "Persist only fixed-length recovery-code digests",
                "Introduce an application-facing step-up policy independent from controller annotations",
                "generalized registration, refresh, recovery, or operations rate-limit policy; that remains a v0.15.0 concern",
                "- [x] protected feature and release-preparation pull requests are merged",
                "- [x] the v0.14.0 tag, JAR, checksum, and GitHub Release are published",
                "A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E"
            );
    }

    private static String normalizeWhitespace(
        String value
    ) {
        return value
            .replaceAll("\\s+", " ")
            .trim();
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
