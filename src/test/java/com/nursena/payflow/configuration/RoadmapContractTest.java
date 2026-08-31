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
    void shouldAlignRoadmapWithPublishedV100()
        throws Exception {

        String projectVersion = readProjectVersion();
        String roadmap = Files.readString(ROADMAP);
        String normalizedRoadmap = normalizeWhitespace(roadmap);

        assertThat(projectVersion)
            .isEqualTo("1.0.0");

        assertThat(roadmap)
            .contains(
                "PayFlow v1.0.0 is the latest tagged and published release",
                "annotated tag `v1.0.0` with tag object `bc1750b17bbdfdcc24764c943afbfd9943e33190`",
                "exact merge/tag target `1adf0b38f1d2da82097c58e41aea4e36a2b4a643`",
                "Release workflow run `33388085847` succeeded",
                "GitHub Release ID `379712093`",
                "published JAR size: `100566872` bytes",
                "published JAR SHA-256: `ed58f5e812e6dfd3ee1ced8f480265bbc6221ff5de760253095762294a9dd8b1`",
                "checksum asset SHA-256: `24d66af06c41b1c5bad37c218c8a20678b7fe85d7cfb717ae28a8916cfd79a5c`",
                "## v0.10.0 — Released: Trusted Client Context",
                "## v0.11.0 — Released: Structured Logging and Request Correlation",
                "## v0.12.0 — Released: JWT Signing-Key Rotation",
                "## v0.13.0 — Released: Account Recovery and Secure Mail Delivery",
                "## v0.14.0 — Released: MFA and Step-Up Authentication",
                "## v0.15.0 — Released: Generalized Abuse Protection and Performance Evidence",
                "## v0.16.0 — Released: Stabilization, Recovery Rehearsals, and API Freeze",
                "## v1.0.0 — Released: Release Hardening and Evidence Closure",
                "Tracking issue: [#189]",
                "Development-start issue: [#190]",
                "Authentication/security closure issue: [#192]",
                "Financial/messaging integrity closure issue: [#194]",
                "Observability/performance closure issue: [#197]",
                "Recovery/migration/API/documentation freeze issue: [#199]",
                "Supply-chain/clean-environment verification issue: [#201]",
                "Release-preparation issue: [#203]",
                "Immutable-publication issue: [#207]",
                "Publication-record issue: [#208]",
                "### Checkpoint 7 — v1.0.0 release preparation",
                "### Checkpoint 8 — Immutable v1.0.0 publication",
                "### Checkpoint 9 — Publication record and release-train closure",
                "- [x] Publish annotated tag `v1.0.0` only from the exact approved release merge",
                "- [x] Require the tag-triggered Release workflow to succeed",
                "- [x] Independently download and verify the published executable JAR and checksum",
                "- [x] Verify published release notes exactly match the reviewed versioned release notes",
                "CP8 closed through issue #207",
                "9756624045",
                "537914002",
                "537914001"
            )
            .doesNotContain(
                "PayFlow v0.16.0 is the latest tagged and published release",
                "## v1.0.0 — Release Preparation",
                "- [ ] Publish annotated tag `v1.0.0` only from the exact approved release merge",
                "immutable tagging and publication remain separate later checkpoints"
            );

        assertThat(normalizedRoadmap)
            .contains(
                "`/api/v1` compatibility boundary",
                "PostgreSQL backup and restore rehearsal",
                "Flyway clean-install and upgrade rehearsal",
                "Redis and Kafka outage/recovery operations",
                "dependency vulnerability review",
                "secret scanning",
                "SBOM",
                "clean-environment release rehearsal",
                "Authentication and security lifecycle closure",
                "Financial and messaging integrity guarantees",
                "Supply-chain and clean-environment release-candidate verification"
            );
    }

    @Test
    void shouldFreezeV013V014AndV015PublicationBoundaries()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] the protected v0.13.0 release-preparation pull request is merged",
                "- [x] the v0.13.0 tag, JAR, checksum, and GitHub Release are published",
                "78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA",
                "- [x] protected feature and release-preparation pull requests are merged",
                "- [x] the v0.14.0 tag, JAR, checksum, and GitHub Release are published",
                "A6533039C5DDBE610D9DDB986DDBDAFE192DD56BE664E86B65A72AECF51F116E",
                "- [x] Pass protected `build-and-test` and `docker-smoke` checks on the exact release-preparation PR head",
                "- [x] Record immutable publication evidence after protected merge and publication",
                "- [x] the v0.15.0 tag, JAR, checksum, and GitHub Release are published",
                "release-preparation PR: `#167`",
                "2f334ca28c78533e5bfc3a2dc5ee3c4a3d903976",
                "c29a067ca3a64514444e17db59a2b862d26f5950",
                "a1aa528b4933c69a3fa81c10a103154bd1d6a327",
                "32172653513",
                "100236578",
                "7EDF5EAD1EB93966E750F917D9472B4383D2B3CDA7406A264AE78B106A779080"
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
