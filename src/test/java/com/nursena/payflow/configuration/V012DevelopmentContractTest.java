package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V012DevelopmentContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

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

        assertThat(Files.readString(DOCKER_SMOKE))
            .contains(
                "openssl genpkey",
                "rsa_keygen_bits:2048",
                "JWT_ACTIVE_KEY_ID: smoke-active-2026-08",
                "JWT_ACTIVE_PRIVATE_KEY_LOCATION: file:/run/secrets/payflow/jwt/active-private.pem",
                ":/run/secrets/payflow/jwt:ro"
            );
    }

    @Test
    void shouldExposeDevelopmentStatusWithoutPublicApiChanges()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "The active `0.12.0-SNAPSHOT` line",
                "stable JWT `kid` issuance",
                "active and previous RSA verification-key overlap",
                "The `production` profile requires configured PKCS#8 private",
                "docs/operations/jwt-key-rotation.md",
                "docs/adr/0012-jwt-signing-key-rotation.md"
            );
    }

    @Test
    void shouldKeepReleaseHistoryAndUnreleasedScopeAligned()
        throws IOException {

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "Stable JWT `kid` issuance",
                "## [0.11.0] - 2026-08-03",
                "[#111](https://github.com/nursena-pc/payflow/pull/111)",
                "## [0.10.0] - 2026-08-01",
                "## [0.9.0] - 2026-07-30",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.11.0...HEAD"
            );
    }
}
