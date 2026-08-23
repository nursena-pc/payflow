package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CleanEnvironmentReleaseRehearsalContractTest {

    private static final Path SCRIPT =
        Path.of(
            "scripts",
            "release",
            "verify-clean-environment-rehearsal.ps1"
        );

    private static final Path DOCUMENTATION =
        Path.of(
            "docs",
            "operations",
            "clean-environment-release-rehearsal.md"
        );

    @Test
    void shouldKeepTheReleaseRehearsalExactHeadLocalOnlyAndFailClosed()
        throws IOException {

        String script =
            Files.readString(SCRIPT)
                .replace("\r\n", "\n");

        String documentation =
            Files.readString(DOCUMENTATION)
                .replace("\r\n", "\n");

        assertThat(script)
            .contains("[Parameter(Mandatory)]")
            .contains("$ExpectedHead")
            .contains("'worktree'")
            .contains("'--detach'")
            .contains("'clean', 'verify'")
            .contains("Get-TestSummary")
            .contains("$DockerfileLines")
            .contains("TrimEnd()")
            .doesNotContain("(?m)^FROM")
            .contains("verify-gitleaks.ps1")
            .contains("verify-vulnerability-review.ps1")
            .contains("MAIL_CONTENT_ENCRYPTION_KEY")
            .contains("valueIntentionallyOmitted = $true")
            .contains("containersCreated = $false")
            .contains("docker-managed-named-volume")
            .contains("10001:10001")
            .contains("0400")
            .contains("0444")
            .contains("dynamic-loopback")
            .contains("rawApplicationLogsRetained = $false")
            .contains("tagCreated = $false")
            .contains("releasePublished = $false")
            .contains("signingClaim = $false")
            .contains("slsaClaim = $false")
            .contains("reproducibleBuildClaim = $false")
            .contains("provenanceAttestationClaim = $false")
            .contains("productionCertificationClaim = $false")
            .doesNotContain("-FilePath 'gh'")
            .doesNotContain("git tag ")
            .doesNotContain("1621")
            .doesNotContain("ad921d70e5362fecf9ff05a6c5b277fabdef373b");

        assertThat(documentation)
            .contains("fresh isolated checkout")
            .contains("Surefire XML")
            .contains("LF and CRLF checkouts")
            .contains("Docker-managed Linux volume")
            .contains("GitHub-hosted merge gates")
            .contains("does not create a Git tag or GitHub Release")
            .contains("does not claim")
            .contains("SLSA provenance")
            .contains("reproducible builds")
            .contains("provenance attestation");
    }
}
