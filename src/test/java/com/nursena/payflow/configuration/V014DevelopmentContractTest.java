package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V014DevelopmentContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");
    @Test
    void shouldExposeV014DevelopmentStatus()
        throws IOException {
        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.13.0 is the latest published release",
                "active development line uses `0.14.0-SNAPSHOT`",
                "## v0.14.0 active development",
                "TOTP-based multi-factor authentication",
                "digest-only login challenge",
                "single-use recovery codes",
                "purpose-bound step-up grants"
            )
            .doesNotContain(
                "Maven version remains `0.13.0`",
                "## v0.14.0 release preparation"
            );
    }
    @Test
    void shouldDefineMfaLifecycleAndSecretBoundaries()
        throws IOException {
        assertThat(Files.readString(ROADMAP))
            .contains(
                "`DISABLED`, `PENDING`, and `ENABLED` lifecycle transitions",
                "Keep MFA state separate from `UserStatus` and email-verification state",
                "Protect every pending or active TOTP secret before PostgreSQL persistence",
                "dedicated MFA secret-protection port",
                "Return the plaintext provisioning secret only in the enrollment response that created it",
                "Activate enrollment only after a valid TOTP proof",
                "one user has at most one effective pending or active authenticator",
                "Exclude secrets, provisioning URIs, TOTP values, protected bytes, and key material from observable output"
            );
    }
    @Test
    void shouldDefineChallengeAndRecoveryCodeBoundaries()
        throws IOException {
        assertThat(Files.readString(ROADMAP))
            .contains(
                "Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed",
                "Persist only a fixed-length challenge digest, expiration, bounded attempt state, and terminal state",
                "Issue no access or refresh credential while an enabled user's challenge remains unresolved",
                "Consume a successful challenge exactly once",
                "concurrent submissions have at most one successful winner",
                "Persist only fixed-length recovery-code digests",
                "Consume every recovery code atomically and at most once",
                "Revoke active refresh-token families after MFA disable or authenticator replacement"
            );
    }
    @Test
    void shouldDefineStepUpPolicyAndExplicitDeferrals()
        throws IOException {
        assertThat(Files.readString(ROADMAP))
            .contains(
                "application-facing step-up policy independent from controller annotations",
                "Bind every step-up grant to one authenticated subject, purpose, issue time, and short expiration",
                "Evaluate dead-letter replay and discard as explicit operator step-up candidates",
                "Reject cross-purpose, expired, replayed, or wrong-subject grants",
                "SMS, voice-call, or email-delivered one-time passwords",
                "WebAuthn, passkeys, FIDO2 security keys, or biometric authentication",
                "generalized registration, refresh, recovery, or operations rate-limit policy; that remains a v0.15.0 concern",
                "access-token denylisting or immediate revocation of already-issued JWTs"
            );
    }
    @Test
    void shouldRetainV013PublicationAndUnreleasedChangelog()
        throws IOException {
        assertThat(Files.readString(ROADMAP))
            .contains(
                "## v0.13.0 — Released: Account Recovery and Secure Mail Delivery",
                "published merge and tag commit: `726f631a0de800870813ccb0c00b2676eb5d172b`",
                "release workflow run: [`31115952987`]",
                "published JAR SHA-256: `78520B04BA3FDAF1BCEB3EAF29FCBE96C46265DF691C52C9048CEE6B5D58F4DA`"
            );
        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.13.0] - 2026-08-06",
                "[0.13.0]: https://github.com/nursena-pc/payflow/compare/v0.12.0...v0.13.0",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.13.0...HEAD"
            );
    }
}
