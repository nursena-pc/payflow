package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .PasswordRecoveryLinkPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryPreparationServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "6bde590c-9f77-4cc5-9fd8-2d74c587ec7a"
        );

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-05T12:30:00Z");

    private static final URI CONFIRMATION_LINK =
        URI.create(
            "https://app.payflow.local/recover-password"
                + "?token=" + CREDENTIAL
        );

    @Mock
    private AccountActionCredentialIssuer
        credentialIssuer;

    @Mock
    private PasswordRecoveryLinkPort recoveryLink;

    private PasswordRecoveryPreparationService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryPreparationService(
            credentialIssuer,
            recoveryLink
        );
    }

    @Test
    void shouldPrepareConfiguredLinkFromIssuedCredential() {
        when(credentialIssuer.issue(
            USER_ID,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        ))
            .thenReturn(
                new IssuedAccountActionCredential(
                    CREDENTIAL,
                    EXPIRES_AT
                )
            );
        when(recoveryLink.build(CREDENTIAL))
            .thenReturn(CONFIRMATION_LINK);

        PreparedPasswordRecovery result =
            service.prepare(USER_ID);

        assertThat(result.confirmationLink())
            .isEqualTo(CONFIRMATION_LINK);
        assertThat(result.expiresAt())
            .isEqualTo(EXPIRES_AT);
        assertThat(result.toString())
            .isEqualTo(
                "PreparedPasswordRecovery[redacted]"
            )
            .doesNotContain(CREDENTIAL);

        verify(credentialIssuer).issue(
            USER_ID,
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        );
        verify(recoveryLink).build(CREDENTIAL);
    }
}
