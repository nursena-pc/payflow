package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V014MfaLoginChallengeContractTest {

    private static final Path ROOT = Path.of("");

    @Test
    void shouldMarkLoginChallengeIncrementCompleteWithoutClaimingRecoveryCodes() throws IOException {
        String roadmap = Files.readString(ROOT.resolve("docs/roadmap.md"));
        assertThat(roadmap).contains(
            "- [x] Preserve existing Redis-backed password-attempt protection before user lookup and password verification",
            "- [x] Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed",
            "- [x] Persist only a fixed-length challenge digest, expiration, bounded attempt state, and terminal state",
            "- [x] Issue no access or refresh credential while an enabled user's challenge remains unresolved",
            "- [x] Consume a successful challenge exactly once before issuing access and refresh credentials",
            "- [x] Reject expired, exhausted, replayed, malformed, and superseded challenges through one stable public contract",
            "- [x] Lock verification so concurrent submissions have at most one successful winner",
            "- [ ] Generate recovery codes from cryptographically secure randomness"
        );
    }

    @Test
    void shouldDocumentDigestOnlyBoundedChallengeContract() throws IOException {
        String security = normalizeWhitespace(
            Files.readString(ROOT.resolve("docs/security/mfa-login-challenge.md"))
        );
        assertThat(security).contains(
            "32 cryptographically secure random bytes",
            "canonical unpadded Base64 URL",
            "32-byte SHA-256 digest",
            "five minutes",
            "five attempts",
            "MFA_CHALLENGE_INVALID",
            "exactly one refresh-token family"
        );
    }

    @Test
    void shouldConfigureBoundedChallengeDefaults() throws IOException {
        String application = Files.readString(ROOT.resolve("src/main/resources/application.yml"));
        assertThat(application).contains(
            "MFA_LOGIN_CHALLENGE_TTL:5m",
            "MFA_LOGIN_CHALLENGE_MAX_ATTEMPTS:5"
        );
    }

    @Test
    void shouldPersistOnlyDigestAndTerminalChallengeState() throws IOException {
        String migration = Files.readString(ROOT.resolve(
            "src/main/resources/db/migration/V19__create_mfa_login_challenges.sql"
        ));
        assertThat(migration).contains(
            "challenge_digest BYTEA NOT NULL",
            "octet_length(challenge_digest) = 32",
            "attempts_remaining INTEGER NOT NULL",
            "'PENDING', 'CONSUMED', 'EXHAUSTED', 'EXPIRED', 'SUPERSEDED'",
            "uq_mfa_login_challenges_pending_user"
        ).doesNotContain("challenge_token", "plaintext_challenge");
    }

    @Test
    void shouldExposePasswordStageAndChallengeConfirmationPublicContracts() throws IOException {
        String readme = Files.readString(ROOT.resolve("README.md"));
        assertThat(readme).contains(
            "202 MFA_REQUIRED",
            "`POST` | `/api/v1/auth/mfa/challenges/confirm`",
            "creates no access token, refresh-token family, or refresh-token record",
            "Concurrent confirmation has at most one successful credential-issuing winner"
        );
    }

    @Test
    void shouldKeepRecoveryAndStepUpOutsideThisIncrement() throws IOException {
        String security = normalizeWhitespace(
            Files.readString(ROOT.resolve("docs/security/mfa-login-challenge.md"))
        );
        assertThat(security).contains(
            "Recovery codes, MFA disable, authenticator replacement, and reusable step-up",
            "outside this increment",
            "Generalized API-wide abuse protection remains a v0.15.0 concern"
        );
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
