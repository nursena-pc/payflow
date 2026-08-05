package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V013DevelopmentContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    @Test
    void shouldExposeV013DevelopmentStatus()
        throws IOException {

        assertThat(Files.readString(README))
            .contains(
                "PayFlow v0.12.0 is the latest published release",
                "the active `0.13.0-SNAPSHOT` line",
                "## v0.13.0 active development",
                "email-ownership verification and secure password recovery",
                "persisted only as digests",
                "revoke every active refresh-token family"
            )
            .doesNotContain(
                "v0.12.0 is in protected release preparation",
                "## v0.12.0 release preparation"
            );
    }

    @Test
    void shouldDefineCredentialAndEnumerationBoundaries()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "at least 256 bits of cryptographically secure randomness",
                "Persist only fixed-length SHA-256 digests, never plaintext credentials",
                "one successful consumption",
                "Return the same accepted response for eligible and ineligible identities",
                "Apply identical limiter work before account eligibility is evaluated",
                "3 requests per hour",
                "20 requests per hour",
                "Fail closed when Redis cannot make a safe abuse-control decision"
            );
    }

    @Test
    void shouldRecordCompletedEmailVerificationIncrement()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "- [x] Issue a verification credential after successful registration",
                "- [x] Add generic `POST /api/v1/auth/email-verification/requests`",
                "- [x] Add token-confirmation `POST /api/v1/auth/email-verification/confirm`",
                "- [x] Build links only from validated configuration, never request host headers",
                "- [x] Mark email ownership exactly once in the confirmation transaction",
                "- [x] Reject login for unverified new users only after credentials match",
                "- [x] Preserve generic behavior for unknown, closed, or already-verified accounts"
            );

        assertThat(Files.readString(README))
            .contains(
                "The third increment connects registration and authentication",
                "/api/v1/auth/email-verification/requests",
                "/api/v1/auth/email-verification/confirm",
                "validated configuration"
            );
    }

    @Test
    void shouldDefinePublicFlowsAndSessionRevocation()
        throws IOException {

        assertThat(Files.readString(ROADMAP))
            .contains(
                "POST /api/v1/auth/email-verification/requests",
                "POST /api/v1/auth/email-verification/confirm",
                "POST /api/v1/auth/password-recovery/requests",
                "POST /api/v1/auth/password-recovery/confirm",
                "Backfill every pre-v0.13.0 user as verified",
                "Revoke all active refresh-token families with `PASSWORD_RECOVERY`",
                "concurrent confirmation has one winner",
                "Preserve the existing short access-token residual-validity boundary"
            );

        assertThat(Files.readString(CHANGELOG))
            .contains(
                "## [Unreleased]",
                "## [0.12.0] - 2026-08-04",
                "[Unreleased]: https://github.com/nursena-pc/payflow/compare/v0.12.0...HEAD"
            );
    }
}
