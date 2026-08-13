package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V014StepUpAuthenticationContractTest {

    private static final Path ROOT = Path.of("");
    private static final Path ROADMAP = ROOT.resolve("docs/roadmap.md");
    private static final Path README = ROOT.resolve("README.md");
    private static final Path SECURITY = ROOT.resolve("docs/security/step-up-authentication.md");
    private static final Path MIGRATION = ROOT.resolve("src/main/resources/db/migration/V21__create_step_up_grants.sql");

    @Test
    void shouldMarkPurposeBoundStepUpCompleteWithAccountSecurityIntegration() throws IOException {
        String roadmap = Files.readString(ROADMAP);
        assertThat(roadmap).contains(
            "### Increment 5 — Step-up authentication",
            "- [x] Introduce an application-facing step-up policy independent from controller annotations",
            "- [x] Bind every step-up grant to one authenticated subject, purpose, issue time, and short expiration",
            "- [x] Reject cross-purpose, expired, superseded, replayed, or wrong-subject grants",
            "### Increment 6 — MFA disable and recovery-code rotation",
            "- [x] Disable the enabled authenticator only after `mfa-disable` step-up succeeds"
        );
    }

    @Test
    void shouldFreezeOpaqueDigestOnlyGrantContract() throws IOException {
        String security = normalizeWhitespace(Files.readString(SECURITY));
        assertThat(security).contains(
            "32 cryptographically secure random bytes",
            "canonical unpadded Base64URL text",
            "43-character plaintext grant",
            "stores only its 32-byte SHA-256 digest",
            "default lifetime is five minutes",
            "longer than fifteen minutes"
        );
    }

    @Test
    void shouldFreezeSubjectPurposeTimeReplayAndSupersessionPolicy() throws IOException {
        String security = normalizeWhitespace(Files.readString(SECURITY));
        assertThat(security).contains(
            "StepUpAuthorizationPolicy",
            "persisted subject equals the authenticated subject",
            "persisted purpose equals the exact operation purpose",
            "grant has not expired",
            "grant has not been superseded",
            "grant has not already been consumed",
            "at most one successful winner"
        );
    }

    @Test
    void shouldPersistOnlyTypedDigestBoundGrantMetadata() throws IOException {
        String migration = Files.readString(MIGRATION);
        assertThat(migration).contains(
            "CREATE TABLE step_up_grants",
            "subject_id UUID NOT NULL",
            "purpose VARCHAR(64) NOT NULL",
            "grant_digest BYTEA NOT NULL",
            "octet_length(grant_digest) = 32",
            "consumed_at TIMESTAMPTZ NULL",
            "superseded_at TIMESTAMPTZ NULL",
            "uq_step_up_grants_digest"
        ).doesNotContain("grant_token", "plaintext_grant");
    }

    @Test
    void shouldRetainFrozenStepUpFailureContractsAndOperatorCandidates() throws IOException {
        String security = normalizeWhitespace(Files.readString(SECURITY));
        assertThat(security).contains(
            "403 STEP_UP_INVALID",
            "403 STEP_UP_REQUIRED",
            "kafka-dead-letter-replay",
            "kafka-dead-letter-discard",
            "does not silently change the operations API"
        );
    }

    @Test
    void shouldExposeStepUpCapabilityInReadmeWithoutClaimingFutureMutations() throws IOException {
        String readme = normalizeWhitespace(Files.readString(README));
        assertThat(readme).contains(
            "purpose-bound step-up authentication",
            "PostgreSQL V21 stores only grant digests",
            "StepUpAuthorizationPolicy",
            "/api/v1/users/me/step-up/grants",
            "MFA disable and recovery-code rotation consume exact step-up purposes",
                "Active-authenticator replacement remains deferred"
        );
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
