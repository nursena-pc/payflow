package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V014TotpEnrollmentContractTest {

    private static final Path ROOT = Path.of("");
    private static final Path ROADMAP = ROOT.resolve("docs/roadmap.md");
    private static final Path README = ROOT.resolve("README.md");
    private static final Path SECURITY = ROOT.resolve("docs/security/mfa-enrollment.md");
    private static final Path APPLICATION = ROOT.resolve("src/main/resources/application.yml");
    private static final Path MIGRATION = ROOT.resolve("src/main/resources/db/migration/V18__create_mfa_authenticators.sql");

    @Test
    void shouldPreserveCompletedEnrollmentIncrementContract() throws IOException {
        String roadmap = Files.readString(ROADMAP);
        assertThat(roadmap).contains(
            "- [x] Require an authenticated, active, email-verified user to begin enrollment",
            "- [x] Generate a high-entropy TOTP secret with a standards-compatible `otpauth://` provisioning value",
            "- [x] Protect every pending or active TOTP secret before PostgreSQL persistence",
            "- [x] Use a dedicated MFA secret-protection port and separate production key material",
            "- [x] Return the plaintext provisioning secret only in the enrollment response that created it",
            "- [x] Activate enrollment only after a valid TOTP proof within the documented clock-skew window",
            "- [x] Serialize replacement so one user has at most one effective pending or active authenticator",
            "- [x] Exclude secrets, provisioning URIs, TOTP values, protected bytes, and key material from observable output",
            "- [x] Issue a short-lived opaque MFA login challenge only after the password and account eligibility checks succeed",
            "- [ ] Generate recovery codes from cryptographically secure randomness",
            "- [ ] Introduce an application-facing step-up policy independent from controller annotations"
        );
    }

    @Test
    void shouldDocumentOneTimeProvisioningAndBoundedTotpProfile() throws IOException {
        String security = Files.readString(SECURITY);
        assertThat(security).contains(
            "160-bit random TOTP secret",
            "canonical unpadded Base32",
            "HMAC-SHA1",
            "six digits",
            "30-second period",
            "one adjacent counter on either side",
            "current password",
            "MFA_STATE_CONFLICT"
        );
    }

    @Test
    void shouldConfigureDedicatedProductionSecretProtection() throws IOException {
        String application = Files.readString(APPLICATION);
        assertThat(application).contains(
            "MFA_TOTP_ISSUER:PayFlow",
            "MFA_ENROLLMENT_TTL:10m",
            "MFA_SECRET_PROTECTION_MODE:ephemeral",
            "MFA_SECRET_ENCRYPTION_KEY:",
            "provider-mode: configured"
        );
    }

    @Test
    void shouldPersistProtectedAuthenticatorStateWithDatabaseConstraints() throws IOException {
        String migration = Files.readString(MIGRATION);
        assertThat(migration).contains(
            "CREATE TABLE mfa_authenticators",
            "user_id UUID PRIMARY KEY",
            "protected_secret BYTEA NOT NULL",
            "state IN ('PENDING', 'ENABLED')",
            "octet_length(protected_secret) >= 49",
            "enrollment_expires_at > created_at"
        ).doesNotContain(
            "plaintext_secret",
            "totp_secret VARCHAR"
        );
    }

    @Test
    void shouldExposeEnrollmentEndpointsAsCompletedPublicContract() throws IOException {
        String readme = Files.readString(README);
        assertThat(readme).contains(
            "`GET` | `/api/v1/users/me/mfa`",
            "`POST` | `/api/v1/users/me/mfa/enrollment`",
            "`POST` | `/api/v1/users/me/mfa/enrollment/confirm`",
            "`DELETE` | `/api/v1/users/me/mfa/enrollment`"
        );
    }

    @Test
    void shouldKeepSecretMaterialOutOfDomainUserAggregate() throws IOException {
        String user = Files.readString(ROOT.resolve("src/main/java/com/nursena/payflow/user/domain/model/User.java"));
        assertThat(user).doesNotContain(
            "totp",
            "MfaAuthenticator",
            "ProtectedMfaSecret",
            "provisioningUri"
        );
    }

    @Test
    void shouldRetainFoundationThreatModelAndAdr() throws IOException {
        assertThat(Files.exists(ROOT.resolve("docs/adr/0014-mfa-and-step-up-authentication.md"))).isTrue();
        assertThat(Files.exists(ROOT.resolve("docs/security/mfa-threat-model.md"))).isTrue();
        assertThat(Files.readString(SECURITY)).contains(
            "This increment does not change login credential issuance",
            "does not change login credential issuance"
        );
    }
}
