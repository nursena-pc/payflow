package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions
    .assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V014MfaFoundationContractTest {

    private static final Path README =
        Path.of("README.md");

    private static final Path CHANGELOG =
        Path.of("CHANGELOG.md");

    private static final Path ROADMAP =
        Path.of("docs", "roadmap.md");

    private static final Path ADR =
        Path.of(
            "docs",
            "adr",
            "0014-mfa-and-step-up-authentication.md"
        );

    private static final Path THREAT_MODEL =
        Path.of(
            "docs",
            "security",
            "mfa-threat-model.md"
        );

    private static final Path MFA_DOMAIN =
        Path.of(
            "src",
            "main",
            "java",
            "com",
            "nursena",
            "payflow",
            "user",
            "domain"
        );

    @Test
    void shouldRecordCompleteMfaThreatModel()
        throws IOException {

        String threatModel =
            normalizeWhitespace(
                Files.readString(THREAT_MODEL)
            );

        assertThat(threatModel)
            .contains(
                "Enrollment hijack",
                "Second-factor bypass",
                "Challenge theft",
                "Brute force",
                "Replay and concurrent verification",
                "Durable plaintext recovery codes",
                "Stolen bearer token disables MFA",
                "Confused-deputy or cross-purpose reuse",
                "In-memory synchronization, JVM-local locks, or optimistic assumptions are not sufficient evidence"
            );
    }

    @Test
    void shouldFreezeLifecycleAndArchitectureDecision()
        throws IOException {

        String adr =
            normalizeWhitespace(
                Files.readString(ADR)
            );

        assertThat(adr)
            .contains(
                "DISABLED -> PENDING",
                "PENDING -> ENABLED",
                "PENDING -> DISABLED",
                "ENABLED -> DISABLED",
                "`UserStatus`, email-verification state, and MFA lifecycle remain separate concepts",
                "application-facing MFA secret-protection port",
                "JPA entities do not enter the MFA domain model"
            );
    }

    @Test
    void shouldFreezeGenericPublicFailureContracts()
        throws IOException {

        String threatModel =
            normalizeWhitespace(
                Files.readString(THREAT_MODEL)
            );

        assertThat(threatModel)
            .contains(
                "MFA_STATE_CONFLICT",
                "MFA_VERIFICATION_FAILED",
                "STEP_UP_REQUIRED",
                "STEP_UP_INVALID",
                "MFA_SECURITY_UNAVAILABLE"
            )
            .contains(
                "malformed, unknown, expired, exhausted, consumed, replayed, and superseded challenge state"
            );
    }

    @Test
    void shouldFreezeRevocationReasonsAndStepUpPurposes()
        throws IOException {

        String threatModel =
            Files.readString(THREAT_MODEL);
        String adr =
            Files.readString(ADR);

        assertThat(adr)
            .contains(
                "`MFA_DISABLED`",
                "`MFA_AUTHENTICATOR_REPLACED`"
            );

        assertThat(threatModel)
            .contains(
                "`mfa-disable`",
                "`recovery-code-rotation`",
                "`mfa-authenticator-replacement`",
                "`kafka-dead-letter-replay`",
                "`kafka-dead-letter-discard`"
            );
    }

    @Test
    void shouldKeepMfaDomainFreeFromFrameworkAndAdapterTypes()
        throws IOException {

        Path lifecycle = MFA_DOMAIN.resolve(
            Path.of("model", "MfaLifecycle.java")
        );
        Path state = MFA_DOMAIN.resolve(
            Path.of("model", "MfaLifecycleState.java")
        );
        Path purpose = MFA_DOMAIN.resolve(
            Path.of("model", "StepUpPurpose.java")
        );

        String source = String.join(
            "\n",
            Files.readString(lifecycle),
            Files.readString(state),
            Files.readString(purpose)
        );

        assertThat(source)
            .doesNotContain(
                "org.springframework",
                "jakarta.persistence",
                "HttpServletRequest",
                "Jwt",
                "JpaEntity"
            );
    }

    @Test
    void shouldRetainFoundationWhileLoginChallengeIncrementAdvances()
        throws IOException {

        String roadmap =
            Files.readString(ROADMAP);

        assertThat(roadmap)
            .contains(
                "- [x] Record MFA enrollment, login, recovery, disable, bypass, replay, and concurrency threats",
                "- [x] Keep MFA state separate from `UserStatus` and email-verification state",
                "- [x] Define `DISABLED`, `PENDING`, and `ENABLED` lifecycle transitions explicitly",
                "- [x] Define stable public errors that do not reveal secrets, recovery-code state, or internal challenge state",
                "- [x] Define account-security refresh-family revocation reasons before implementation",
                "- [x] Keep controllers, JWT adapters, and JPA entities outside the MFA domain model"
            )
            .contains(
                "- [x] Generate a high-entropy TOTP secret with a standards-compatible `otpauth://` provisioning value",
                "- [x] Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed",
                "- [ ] Generate recovery codes from cryptographically secure randomness",
                "- [ ] Introduce an application-facing step-up policy independent from controller annotations"
            );
    }

    @Test
    void shouldExposeFoundationWithoutClaimingRuntimeMfa()
        throws IOException {

        String readme =
            normalizeWhitespace(
                Files.readString(README)
            );
        String changelog =
            normalizeWhitespace(
                Files.readString(CHANGELOG)
            );

        assertThat(readme)
            .contains(
                "The first delivery increment now freezes the domain lifecycle, typed step-up purpose vocabulary, account-security refresh-family revocation reasons",
                "No MFA endpoint, authenticator persistence, TOTP verification, recovery-code implementation, or runtime step-up enforcement is introduced by this foundation increment"
            );

        assertThat(changelog)
            .contains(
                "Domain-only MFA lifecycle foundation",
                "Typed step-up purpose vocabulary",
                "ADR 0014 and a versioned MFA threat model"
            );
    }

    private static String normalizeWhitespace(
        String value
    ) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
