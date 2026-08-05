package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .EmailVerificationLinkPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationPreparationServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "ee9acd5d-620f-4876-913d-ffb85e673c6a"
        );

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-06T12:00:00Z");

    private static final URI CONFIRMATION_LINK =
        URI.create(
            "https://app.payflow.local/verify-email"
                + "?token=" + CREDENTIAL
        );

    @Mock
    private AccountActionCredentialIssuer
        credentialIssuer;

    @Mock
    private EmailVerificationLinkPort verificationLink;

    private EmailVerificationPreparationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationPreparationService(
            credentialIssuer,
            verificationLink
        );
    }

    @Test
    void shouldPrepareConfiguredLinkFromIssuedCredential() {
        when(credentialIssuer.issue(
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        ))
            .thenReturn(
                new IssuedAccountActionCredential(
                    CREDENTIAL,
                    EXPIRES_AT
                )
            );
        when(verificationLink.build(CREDENTIAL))
            .thenReturn(CONFIRMATION_LINK);

        PreparedEmailVerification result =
            service.prepare(USER_ID);

        assertThat(result.confirmationLink())
            .isEqualTo(CONFIRMATION_LINK);
        assertThat(result.expiresAt())
            .isEqualTo(EXPIRES_AT);
        assertThat(result.toString())
            .isEqualTo(
                "PreparedEmailVerification[redacted]"
            )
            .doesNotContain(CREDENTIAL);

        verify(credentialIssuer).issue(
            USER_ID,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        );
        verify(verificationLink).build(CREDENTIAL);
    }
}
