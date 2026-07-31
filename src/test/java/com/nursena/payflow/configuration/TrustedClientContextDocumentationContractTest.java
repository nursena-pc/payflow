package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TrustedClientContextDocumentationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path LOGIN_GUIDE =
        Path.of(
            "docs",
            "login-rate-limiting.md"
        );

    private static final Path ARCHITECTURE =
        Path.of(
            "docs",
            "architecture.md"
        );

    private static final Path ARCHITECTURE_DIAGRAM =
        Path.of(
            "docs",
            "diagrams",
            "architecture.mmd"
        );

    private static final Path ADR =
        Path.of(
            "docs",
            "adr",
            "0011-trusted-client-context.md"
        );

    private static final Path ROADMAP =
        Path.of(
            "docs",
            "roadmap.md"
        );

    @Test
    void shouldDocumentTrustedProxyConfiguration()
        throws IOException {

        String readme =
            Files.readString(README);

        String loginGuide =
            Files.readString(LOGIN_GUIDE);

        assertThat(readme)
            .contains(
                "spoofing-resistant effective client address",
                "trusted reverse-proxy CIDR validation"
            );

        assertThat(loginGuide)
            .contains(
                "TRUSTED_PROXY_CIDRS",
                "FORWARDED_HEADER_MAX_LENGTH",
                "FORWARDED_MAX_HOPS",
                "0.0.0.0/0",
                "::/0"
            );
    }

    @Test
    void shouldDocumentDeterministicHeaderPolicy()
        throws IOException {

        String loginGuide =
            Files.readString(LOGIN_GUIDE);

        String adr =
            Files.readString(ADR);

        assertThat(loginGuide)
            .contains(
                "use `Forwarded` when it is present",
                "otherwise use `X-Forwarded-For`",
                "parse the chain from right to left",
                "select the first untrusted address"
            );

        assertThat(adr)
            .contains(
                "## Header precedence",
                "## Chain resolution",
                "do not downgrade to `X-Forwarded-For`"
            );
    }

    @Test
    void shouldDocumentSafeFallbacks()
        throws IOException {

        String loginGuide =
            Files.readString(LOGIN_GUIDE);

        String adr =
            Files.readString(ADR);

        assertThat(loginGuide)
            .contains(
                "fail safely to the direct peer",
                "oversized",
                "excessive hop counts"
            );

        assertThat(adr)
            .contains(
                "## Failure behavior",
                "The direct peer is used when:",
                "A malformed preferred `Forwarded` value"
            );
    }

    @Test
    void shouldDocumentBoundedPrivateObservability()
        throws IOException {

        String loginGuide =
            Files.readString(LOGIN_GUIDE);

        String adr =
            Files.readString(ADR);

        assertThat(loginGuide)
            .contains(
                "payflow.security.client_context.decisions",
                "fixed matrix of 21",
                "The observer API receives only enum dimensions"
            );

        assertThat(adr)
            .contains(
                "source=direct_peer|forwarded|x_forwarded_for",
                "outcome=direct|resolved|untrusted_peer",
                "The observer API does not accept an address"
            );
    }

    @Test
    void shouldShowTrustBoundaryInArchitecture()
        throws IOException {

        String architecture =
            Files.readString(ARCHITECTURE);

        String diagram =
            Files.readString(
                ARCHITECTURE_DIAGRAM
            );

        assertThat(architecture)
            .contains(
                "### Client-context module",
                "### Trusted client-address boundary",
                "first untrusted address"
            );

        assertThat(diagram)
            .contains(
                "Direct peer in trusted CIDR?",
                "ignore forwarding headers",
                "Resolve first untrusted hop from right"
            );
    }

    @Test
    void shouldKeepCiAndReleaseWorkOpen()
        throws IOException {

        String roadmap =
            Files.readString(ROADMAP);

        assertThat(roadmap)
            .contains(
                "- [x] Add an ADR for the proxy trust model",
                "- [x] Run the complete Maven verification suite",
                "- [x] OpenAPI and operations documentation match the implementation",
                "- [ ] Pass protected-branch CI and review checks",
                "- [ ] Publish v0.10.0 release notes and artifacts",
                "- [ ] protected-branch CI passes",
                "- [ ] v0.10.0 release assets and checksum are published"
            );
    }
}
