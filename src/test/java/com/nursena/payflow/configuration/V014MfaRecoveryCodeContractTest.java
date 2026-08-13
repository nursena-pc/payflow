package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V014MfaRecoveryCodeContractTest {

    private static final Path ROOT = Path.of("");
    private static final Path ROADMAP = ROOT.resolve("docs/roadmap.md");
    private static final Path README = ROOT.resolve("README.md");
    private static final Path SECURITY = ROOT.resolve(
        "docs/security/mfa-recovery-codes.md"
    );
    private static final Path MIGRATION = ROOT.resolve(
        "src/main/resources/db/migration/V20__create_mfa_recovery_codes.sql"
    );

    @Test
    void shouldMarkRecoveryCodeGenerationAndLoginConsumptionComplete()
        throws IOException {
        String roadmap = Files.readString(ROADMAP);

        assertThat(roadmap).contains(
            "### Increment 4 — Recovery codes",
            "- [x] Generate recovery codes from cryptographically secure randomness",
            "- [x] Return plaintext recovery codes once when TOTP enrollment is activated",
            "- [x] Persist only fixed-length recovery-code digests",
            "- [x] Consume every recovery code atomically and at most once",
            "- [x] Make recovery-code and TOTP challenge failures indistinguishable at the public boundary",
            "- [x] Rotate recovery codes only after a recent purpose-bound step-up proof exists"
        );
    }

    @Test
    void shouldFreezeRecoveryCodeShapeAndOneTimeDisclosure()
        throws IOException {
        String security = normalizeWhitespace(
            Files.readString(SECURITY)
        );

        assertThat(security).contains(
            "exactly ten independent recovery codes",
            "128 bits of `SecureRandom` entropy",
            "canonical unpadded Base64URL text",
            "producing 22 characters",
            "returns the plaintext set once",
            "No plaintext recovery code is persisted"
        );
    }

    @Test
    void shouldPersistOnlyFixedLengthDigests()
        throws IOException {
        String migration = Files.readString(MIGRATION);

        assertThat(migration).contains(
            "CREATE TABLE mfa_recovery_codes",
            "code_digest BYTEA NOT NULL",
            "octet_length(code_digest) = 32",
            "UNIQUE (code_digest)",
            "consumed_at TIMESTAMPTZ NULL",
            "ix_mfa_recovery_codes_user_unconsumed"
        ).doesNotContain(
            "recovery_code VARCHAR",
            "plaintext",
            "code_plaintext"
        );
    }

    @Test
    void shouldUseOneGenericLoginFailureBoundary()
        throws IOException {
        String security = normalizeWhitespace(
            Files.readString(SECURITY)
        );

        assertThat(security).contains(
            "six-digit value follows the TOTP path",
            "22-character Base64URL value follows the recovery-code path",
            "same public contract",
            "401 MFA_CHALLENGE_INVALID",
            "already consumed rows without revealing their state"
        );
    }

    @Test
    void shouldExposeStepUpProtectedRotationAndDeferReplacement()
        throws IOException {
        String roadmap = Files.readString(ROADMAP);
        String security = normalizeWhitespace(
            Files.readString(SECURITY)
        );

        assertThat(roadmap).contains(
            "### Increment 5 — Step-up authentication",
            "### Increment 6 — MFA disable and recovery-code rotation",
            "- [x] Require the exact recent step-up purpose before MFA disable or recovery-code rotation"
        );
        assertThat(security).contains(
            "Explicit recovery-code rotation is available through an authenticated public",
            "`recovery-code-rotation` step-up grant"
        );
    }

    @Test
    void shouldExposeRecoveryCodeBehaviorInReadme()
        throws IOException {
        String readme = normalizeWhitespace(
            Files.readString(README)
        );

        assertThat(readme).contains(
            "ten independent 128-bit canonical Base64URL recovery codes",
            "PostgreSQL V20 stores only SHA-256 digests",
            "unused recovery code",
            "recovery-code contract"
        );
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
