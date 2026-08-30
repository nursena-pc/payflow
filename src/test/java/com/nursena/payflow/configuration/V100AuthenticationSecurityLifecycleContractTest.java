package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V100AuthenticationSecurityLifecycleContractTest {

    private static final Path CURRENT =
        Path.of(
            "docs",
            "security",
            "v1-authentication-security-lifecycle.md"
        );

    private static final Path THREAT_MODEL =
        Path.of(
            "docs",
            "security",
            "mfa-threat-model.md"
        );

    private static final Path ENROLLMENT_HISTORY =
        Path.of(
            "docs",
            "security",
            "mfa-enrollment.md"
        );

    private static final Path OPERATIONS =
        Path.of(
            "docs",
            "operations",
            "mfa-security.md"
        );

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ENROLLMENT_CONTROLLER =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "user",
            "adapter",
            "in",
            "web",
            "MfaEnrollmentController.java"
        );

    @Test
    void shouldRecordCurrentV1AuthenticationSecurityLifecycle()
        throws IOException {

        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(current)
            .contains(
                "Status: Active v1.0.0 release-candidate security contract",
                "Tracking issue: #192",
                "Project version: `1.0.0`",
                "Registration remains evidence-backed `DEFER`",
                "Password login retains its existing separate Redis-backed fixed-window limiter",
                "`mfa-disable`",
                "`recovery-code-rotation`",
                "active authenticator replacement remains deferred",
                "generic accepted-response boundary",
                "Opaque refresh credentials are stored only as SHA-256 digests"
            );
    }

    @Test
    void shouldAlignCurrentThreatAndOperationsLanguage()
        throws IOException {

        String threatModel =
            Files.readString(THREAT_MODEL);
        String operations =
            Files.readString(OPERATIONS);

        assertThat(threatModel)
            .contains(
                "Status: Historical v0.14.0 foundation security contract retained as an invariant baseline",
                "current v1.0.0 release-candidate lifecycle",
                "plus the current password",
                "The application-facing MFA secret-protection port"
            )
            .doesNotContain(
                "Status: Active v0.14.0 security contract",
                "implementation increment must define whether",
                "A future application-facing MFA secret-protection port"
            );

        assertThat(operations)
            .contains(
                "PayFlow `1.0.0` release-preparation candidate",
                "before enabling production MFA traffic",
                "Active-authenticator replacement remains outside the v1.0.0 release-candidate scope"
            )
            .doesNotContain(
                "before enabling v0.14.0 traffic",
                "Active-authenticator replacement is not part of v0.14.0"
            );
    }

    @Test
    void shouldPreserveHistoricalIncrementEvidence()
        throws IOException {

        String enrollment =
            Files.readString(ENROLLMENT_HISTORY);
        String current =
            Files.readString(CURRENT);

        assertThat(enrollment)
            .contains(
                "Status: Implemented v0.14.0 increment 2",
                "Explicit non-goals of increment 2"
            );

        assertThat(current)
            .contains(
                "`docs/security/mfa-enrollment.md`",
                "`docs/security/mfa-login-challenge.md`",
                "`docs/security/mfa-recovery-codes.md`",
                "`docs/security/step-up-authentication.md`",
                "`docs/security/abuse-protection-threat-model.md`"
            );
    }

    @Test
    void shouldAnchorImplementedEnrollmentProofAndReleaseNavigation()
        throws IOException {

        String controller =
            Files.readString(ENROLLMENT_CONTROLLER);
        String readme =
            normalizeWhitespace(
                Files.readString(README)
            );
        String changelog =
            normalizeWhitespace(
                Files.readString(CHANGELOG)
            );

        assertThat(controller)
            .contains(
                "@PostMapping(\"/enrollment\")",
                "request.currentPassword()"
            );

        assertThat(readme)
            .contains(
                "Checkpoints 1 through 6 are complete",
                "authentication/security lifecycle closure #192",
                "release-preparation checkpoint is tracked by [issue #203]"
            );

        assertThat(changelog)
            .contains(
                "authentication/security lifecycle documentation drift through issue #192",
                "leaving runtime, API, migration, limiter, and authentication behavior unchanged"
            );
    }

    @Test
    void shouldCloseRoadmapCheckpointWithoutAuthenticationExpansion()
        throws IOException {

        String roadmap =
            Files.readString(ROADMAP);
        String current =
            normalizeWhitespace(
                Files.readString(CURRENT)
            );

        assertThat(roadmap)
            .contains(
                "Authentication/security closure issue: [#192]",
                "- [x] Open the Maven development line at `1.0.0-SNAPSHOT` through a protected PR",
                "- [x] Add executable development contracts for the approved v1 release-candidate scope",
                "- [x] Re-verify email verification, password recovery, JWT key rotation, MFA/recovery codes, step-up, session/revocation, abuse-protection, and login-limiter contracts",
                "- [x] Align threat-model and security documentation with implemented behavior",
                "- [x] Preserve credential redaction, anti-enumeration, trusted-client, and fail-closed boundaries",
                "- [x] Resolve only verified release-blocking defects without expanding authentication scope for version branding",
                "No runtime defect was identified"
            );

        assertThat(current)
            .contains(
                "CP2 does not add or activate",
                "registration abuse-protection enforcement",
                "WebAuthn, passkeys, FIDO2",
                "access-token denylisting",
                "new Kafka step-up enforcement"
            );
    }

    private static String normalizeWhitespace(
        String value
    ) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
