package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CleanCheckoutSecurityEvidenceContractTest {

    private static final Path GITLEAKS_VERIFIER =
        Path.of(
            "scripts",
            "security",
            "verify-gitleaks.ps1"
        );

    private static final Path SUPPLY_CHAIN_GENERATOR =
        Path.of(
            "scripts",
            "security",
            "generate-sbom-provenance.ps1"
        );

    private static final Path VULNERABILITY_VERIFIER =
        Path.of(
            "scripts",
            "security",
            "verify-vulnerability-review.ps1"
        );

    @Test
    void shouldProbeIgnoredRuntimeDirectoryWithoutRequiringItToExist()
        throws IOException {

        assertRuntimeDirectoryProbe(GITLEAKS_VERIFIER);
        assertRuntimeDirectoryProbe(SUPPLY_CHAIN_GENERATOR);
        assertRuntimeDirectoryProbe(VULNERABILITY_VERIFIER);
    }

    private static void assertRuntimeDirectoryProbe(Path script)
        throws IOException {

        String content =
            Files.readString(script)
                .replace("\r\n", "\n");

        assertThat(content)
            .as(script.toString())
            .contains("'check-ignore'")
            .contains("'.runtime/'");
    }
}
