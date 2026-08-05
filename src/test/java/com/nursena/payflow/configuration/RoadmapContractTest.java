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

        assertThat(projectVersion)
            .isEqualTo("0.13.0-SNAPSHOT");

        assertThat(roadmap)
            .contains(
                "PayFlow v0.12.0 is the latest tagged release",
                "the Maven version `" + projectVersion + "`",
                "## v0.10.0 — Released: Trusted Client Context",
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "## v0.12.0 — Released: JWT Signing-Key Rotation",
                "## v0.13.0 — Active Development: Email Verification & Password Recovery",
                "fb0f97d076864cf3e45aabe0e3c25c81520ee101",
                "email-ownership verification and secure password recovery"
            )
            .doesNotContain(
                "The v0.12.0 release candidate uses",
                "## v0.12.0 — Release Candidate: JWT Signing-Key Rotation"
            );
    }

    @Test
    void shouldCloseV012PublicationAndOpenV013DeliveryGates()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Open the dedicated v0.12.0 implementation issue #112",
                "- [x] Pass the complete Maven verification suite through protected CI",
                "- [x] Pass protected `build-and-test` and Docker smoke CI for PR #113",
                "- [x] Merge the JWT signing-key rotation increment through protected PR #113",
                "- [x] Close implementation issue #112 after merge",
                "- [x] Prepare v0.12.0 release notes after the implementation PR is merged",
                "- [x] Merge v0.12.0 release preparation through protected PR #114",
                "- [x] v0.12.0 release preparation and publication gates complete",
                "- [x] focused and complete Maven verification pass through protected CI",
                "- [x] Open the dedicated v0.13.0 implementation issue under release train #106",
                "- [x] Add nullable `email_verified_at` as an invariant separate from `UserStatus`",
                "- [x] Backfill every pre-v0.13.0 user as verified to prevent migration lockout",
                "- [x] Register new users without a verified-email timestamp",
                "- [x] Add Flyway V15 with constrained account-action token persistence",
                "- [ ] Persist only fixed-length SHA-256 digests, never plaintext credentials",
                "- [ ] Revoke all active refresh-token families with `PASSWORD_RECOVERY`",
                "- [ ] request responses do not disclose account existence or eligibility"
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
