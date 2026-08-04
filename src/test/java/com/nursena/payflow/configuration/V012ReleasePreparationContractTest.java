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

class V012ReleasePreparationContractTest {

    private static final Path POM =
        Path.of("pom.xml");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path RELEASE_NOTES =
        Path.of(
            "docs",
            "releases",
            "v0.12.0.md"
        );

    private static final Path ADR =
        Path.of(
            "docs",
            "adr",
            "0012-jwt-signing-key-rotation.md"
        );

    private static final Path OPERATIONS_GUIDE =
        Path.of(
            "docs",
            "operations",
            "jwt-key-rotation.md"
        );

    private static final Path APPLICATION_CONFIGURATION =
        Path.of(
            "src",
            "main",
            "resources",
            "application.yml"
        );

    private static final Path DOCKER_SMOKE =
        Path.of(
            ".github",
            "workflows",
            "docker-smoke.yml"
        );

    private static final Path RELEASE_WORKFLOW =
        Path.of(
            ".github",
            "workflows",
            "release.yml"
        );

    @Test
    void shouldUseFinalReleaseCandidateVersion()
        throws Exception {

        assertThat(readProjectVersion())
            .isEqualTo("0.12.0");

        assertThat(Files.readString(README))
            .contains(
                "v0.12.0 is in protected release preparation",
                "docs/releases/v0.12.0.md",
                "v0.12.0 release preparation",
                "verified merge commit of the release-preparation pull request"
            )
            .doesNotContain(
                "The active `0.12.0-SNAPSHOT` line",
                "The active v0.12.0 development line"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "The v0.12.0 release candidate uses",
                "the Maven version `0.12.0`",
                "## v0.12.0 — Release Candidate: JWT Signing-Key Rotation"
            )
            .doesNotContain(
                "0.12.0-SNAPSHOT",
                "## v0.12.0 — Active Development: JWT Signing-Key Rotation"
            );
    }

    @Test
    void shouldDocumentKeySelectionAndSecurityBoundary()
        throws IOException {

        assertThat(Files.readString(ADR))
            .contains(
                "adapter-local `JwtKeyProvider` contract",
                "exactly one active RSA signing key",
                "at most one previous, verification-only public key",
                "Verification is also pinned to RS256",
                "Unknown or absent `kid` values fail authentication",
                "The previous key is verification-only",
                "future KMS or HSM adapter"
            );
    }

    @Test
    void shouldDocumentRotationRollbackAndEmergencyRecovery()
        throws IOException {

        assertThat(Files.readString(OPERATIONS_GUIDE))
            .contains(
                "## Planned rotation",
                "## Rollback",
                "## Emergency compromise response",
                "active A private/public key",
                "previous B public key",
                "access-token TTL plus the deployment's",
                "no dynamic or scheduled key reload",
                "no public JWKS endpoint"
            );
    }

    @Test
    void shouldRequireConfiguredKeysInProduction()
        throws IOException {

        assertThat(
            Files.readString(APPLICATION_CONFIGURATION)
        )
            .contains(
                "provider-mode: ${JWT_KEY_PROVIDER_MODE:ephemeral}",
                "active-key-id: ${JWT_ACTIVE_KEY_ID:local-development}",
                "previous-key-id: ${JWT_PREVIOUS_KEY_ID:}",
                "on-profile: production",
                "provider-mode: configured"
            );

        String dockerSmoke =
            Files.readString(DOCKER_SMOKE);

        assertThat(dockerSmoke)
            .contains(
                "openssl genpkey",
                "rsa_keygen_bits:2048",
                "JWT_ACTIVE_KEY_ID: smoke-active-2026-08",
                "JWT_ACTIVE_PRIVATE_KEY_LOCATION: file:/run/secrets/payflow/jwt/active-private.pem",
                ":/run/secrets/payflow/jwt:ro",
                "chmod 0400 .runtime/jwt/active-private.pem",
                "chmod 0444 .runtime/jwt/active-public.pem",
                "sudo chown -R 10001:10001 .runtime/jwt",
                "Smoke override was not created; no stack logs are available.",
                "Smoke override was not created; no stack teardown is required."
            );

        int ownershipTransferIndex =
            dockerSmoke.indexOf(
                "sudo chown -R 10001:10001 .runtime/jwt"
            );

        assertThat(
            dockerSmoke.indexOf(
                "chmod 0400 .runtime/jwt/active-private.pem"
            )
        )
            .isLessThan(ownershipTransferIndex);

        assertThat(
            dockerSmoke.indexOf(
                "chmod 0444 .runtime/jwt/active-public.pem"
            )
        )
            .isLessThan(ownershipTransferIndex);
    }

    @Test
    void shouldDocumentReleaseScopeVerificationAndAssets()
        throws IOException {

        assertThat(Files.readString(RELEASE_NOTES))
            .contains(
                "# PayFlow v0.12.0",
                "### Stable key identification",
                "### Active and previous verification overlap",
                "### Configured production key loading",
                "### Provider boundary",
                "## Security boundaries",
                "## Verification",
                "protected `build-and-test` CI passed for PR #113",
                "production-profile Docker smoke passed for PR #113",
                "PR #113 was merged through the protected workflow",
                "issue #112 was closed after merge",
                "## Upgrade notes",
                "No database migration is included.",
                "## Release assets",
                "`payflow-0.12.0.jar`",
                "`payflow-0.12.0.jar.sha256`",
                "`v0.11.0...v0.12.0`"
            );
    }

    @Test
    void shouldFreezeChangelogAtV012()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.12.0] - 2026-08-04",
                "Stable JWT `kid` issuance",
                "[#112](https://github.com/nursena-pc/payflow/issues/112)",
                "[#113](https://github.com/nursena-pc/payflow/pull/113)",
                "[0.12.0]: https://github.com/nursena-pc/payflow/compare/v0.11.0...v0.12.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.12.0...HEAD"
            );
    }

    @Test
    void shouldKeepPublicationCriteriaOpenUntilProtectedReleaseCompletes()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Pass protected `build-and-test` and Docker smoke CI for PR #113",
                "- [x] Prepare v0.12.0 release notes after the implementation PR is merged",
                "- [ ] Merge v0.12.0 release preparation through a protected pull request",
                "- [ ] Tag the verified release merge commit as `v0.12.0`",
                "- [ ] Publish the executable JAR, SHA-256 checksum, and GitHub Release",
                "- [ ] v0.12.0 release preparation and publication gates complete"
            );
    }

    @Test
    void shouldRetainTagTriggeredVerifiedReleaseWorkflow()
        throws IOException {

        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains(
                "name: Release",
                "v[0-9]+.[0-9]+.[0-9]+",
                "mvn -B -ntp clean verify",
                "sha256sum",
                "actions/upload-artifact@v7",
                "gh release create",
                "--verify-tag"
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
