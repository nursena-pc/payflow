package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class V015AbuseProtectionFoundationContractTest {

    private static final Path POLICY_DIRECTORY =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "abuseprotection",
            "application",
            "policy"
        );

    private static final Path APPLICATION_YAML =
        Path.of(
            "src",
            "main",
            "resources",
            "application.yml"
        );

    private static final Path ADR =
        Path.of(
            "docs",
            "adr",
            "0015-generalized-abuse-protection.md"
        );

    private static final Path THREAT_MODEL =
        Path.of(
            "docs",
            "security",
            "abuse-protection-threat-model.md"
        );

    private static final Path GUIDE =
        Path.of(
            "docs",
            "abuse-protection.md"
        );

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldKeepApplicationPolicyAdapterIndependent()
        throws IOException {

        List<Path> policyFiles;

        try (var paths = Files.list(POLICY_DIRECTORY)) {
            policyFiles = paths
                .filter(path ->
                    path.getFileName()
                        .toString()
                        .endsWith(".java")
                )
                .toList();
        }

        assertThat(policyFiles).hasSize(4);

        for (Path policyFile : policyFiles) {
            assertThat(Files.readString(policyFile))
                .doesNotContain(
                    "org.springframework",
                    "jakarta.servlet",
                    "javax.servlet",
                    "HttpServlet",
                    "Redis",
                    "Controller"
                );
        }
    }

    @Test
    void shouldDefineBoundedWorkflowAndPolicyTypes()
        throws IOException {

        String workflow = Files.readString(
            POLICY_DIRECTORY.resolve(
                "AbuseProtectionWorkflow.java"
            )
        );

        String policy = Files.readString(
            POLICY_DIRECTORY.resolve(
                "AbuseProtectionPolicy.java"
            )
        );

        assertThat(workflow)
            .contains(
                "REGISTRATION",
                "EMAIL_VERIFICATION_REQUEST",
                "PASSWORD_RECOVERY_REQUEST",
                "MFA_LOGIN_CHALLENGE_CONFIRMATION",
                "STEP_UP_GRANT_ISSUANCE"
            );

        assertThat(policy)
            .contains(
                "Duration.ofSeconds(1)",
                "Duration.ofDays(1)",
                "1_000_000",
                "AbuseProtectionFailureMode"
            );
    }

    @Test
    void shouldConfigureEverySelectedWorkflow()
        throws IOException {

        assertThat(Files.readString(APPLICATION_YAML))
            .contains(
                "abuse-protection:",
                "${ABUSE_PROTECTION_ENABLED:false}",
                "registration:",
                "email-verification-request:",
                "password-recovery-request:",
                "mfa-login-challenge-confirmation:",
                "step-up-grant-issuance:",
                "dependency-failure-mode:",
                "FAIL_CLOSED",
                "login-rate-limit:"
            );
    }

    @Test
    void shouldDocumentThreatsAndFailureContracts()
        throws IOException {

        String adr = normalizeWhitespace(
            Files.readString(ADR)
        );

        String guide = normalizeWhitespace(
            Files.readString(GUIDE)
        );

        assertThat(adr)
            .contains(
                "application policy has no Spring, servlet, HTTP, controller, or Redis dependency",
                "global switch defaults to `false`",
                "generic accepted response while suppressing the protected side effect",
                "existing `LoginRateLimitPort`"
            );

        assertThat(Files.readString(THREAT_MODEL))
            .contains(
                "Attacker capabilities",
                "Trust boundaries",
                "Forwarding-header spoofing",
                "Account enumeration",
                "Dependency-failure bypass",
                "Credential disclosure"
            );

        assertThat(guide)
            .contains(
                "Policy code contains no controller, servlet, HTTP, Spring, or Redis dependency.",
                "windows range from one second through one day",
                "limits range from one through one million",
                "`LOGIN_RATE_LIMIT_*` variables remain unchanged"
            );
    }

    @Test
    void shouldRecordCompletedIncrementWithoutClaimingEnforcement()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "Increment 1 freezes five bounded workflow identifiers",
                "global generalized policy switch defaults off",
                "issue #151"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "Application-facing generalized abuse-protection policy",
                "Validated endpoint-specific windows",
                "issue #151"
            );

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Define protected workflows, attacker capabilities, bypass risks, and trust boundaries",
                "- [x] Add executable development contracts for the approved v0.15.0 scope",
                "Generalized enforcement remains disabled",
                "### Increment 2",
                "Shared Redis enforcement foundation"
            );
    }

    private static String normalizeWhitespace(
        String value
    ) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
