package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SupplyChainEvidenceContractTest {

    private static final Path GENERATOR =
        Path.of(
            "scripts",
            "security",
            "generate-sbom-provenance.ps1"
        );

    private static final Path DOCUMENTATION =
        Path.of(
            "docs",
            "security",
            "supply-chain-evidence.md"
        );

    @Test
    void shouldPinRepeatableSbomAndLocalEvidenceBoundary()
        throws IOException {

        String generator =
            Files.readString(GENERATOR);

        assertThat(generator)
            .contains(
                "[Parameter(Mandatory)]",
                "[ValidatePattern('^[0-9a-f]{40}$')]",
                "Supply-chain evidence generation requires a clean working tree.",
                "git",
                "'diff'",
                "'--check'",
                "3.9.16",
                "distributionSha256Sum",
                "ghcr.io/anchore/syft@sha256:1288ea4c8b38767b4e620c1e312c8cb26b6e887a99b4f07ab6cd19fc6f225026",
                "1.50.0",
                "cyclonedx-json",
                "dependency-tree.txt",
                "git-tree-manifest.txt",
                "local-build-provenance.json",
                "artifactSha256",
                "evidenceSha256",
                "githubHostedWorkflowEvidence = $false",
                "slsaClaim = $false",
                "reproducibleBuildClaim = $false",
                "signingClaim = $false",
                "provenanceAttestationClaim = $false",
                "productionCertificationClaim = $false",
                "releasePublicationClaim = $false"
            );

        String documentation =
            Files.readString(DOCUMENTATION);

        assertThat(documentation)
            .contains(
                "# Supply-chain evidence",
                "CycloneDX",
                "Syft 1.50.0",
                "28d8ff195f4b76632ec620db81426540baf34674",
                "GitHub-hosted CI and Docker Smoke results remain separate merge-gate evidence.",
                "not a SLSA attestation",
                "not a signature",
                "not a claim of reproducible builds"
            )
            .doesNotContain(
                "SLSA certified",
                "production certified",
                "reproducible build guaranteed"
            );
    }
}
