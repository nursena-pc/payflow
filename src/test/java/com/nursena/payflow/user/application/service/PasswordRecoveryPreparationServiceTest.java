package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.application.port.out.AccountActionMailPort;
import com.nursena.payflow.user.application.port.out.PasswordRecoveryLinkPort;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryPreparationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "6bde590c-9f77-4cc5-9fd8-2d74c587ec7a"
    );
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "5e574850-dda3-444f-baba-87e1a1ab41e2"
    );
    private static final EmailAddress EMAIL =
        EmailAddress.of("nursena@example.com");
    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-05T12:30:00Z");
    private static final URI CONFIRMATION_LINK = URI.create(
        "https://app.payflow.local/recover-password?token=" + CREDENTIAL
    );

    @Mock
    private AccountActionCredentialIssuer credentialIssuer;
    @Mock
    private PasswordRecoveryLinkPort recoveryLink;
    @Mock
    private AccountActionMailPort accountActionMail;

    private PasswordRecoveryPreparationService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryPreparationService(
            credentialIssuer,
            recoveryLink,
            accountActionMail
        );
    }

    @Test
    void shouldPrepareAndEnqueueConfiguredLink() {
        when(credentialIssuer.issue(
            USER_ID,
            AccountActionCredentialPurpose.PASSWORD_RECOVERY
        )).thenReturn(
            new IssuedAccountActionCredential(
                CREDENTIAL_ID,
                CREDENTIAL,
                EXPIRES_AT
            )
        );
        when(recoveryLink.build(CREDENTIAL))
            .thenReturn(CONFIRMATION_LINK);

        PreparedPasswordRecovery result = service.prepare(
            USER_ID,
            EMAIL
        );

        assertThat(result.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(result.confirmationLink()).isEqualTo(CONFIRMATION_LINK);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.toString())
            .isEqualTo("PreparedPasswordRecovery[redacted]")
            .doesNotContain(CREDENTIAL);

        ArgumentCaptor<AccountActionMail> mailCaptor =
            ArgumentCaptor.forClass(AccountActionMail.class);
        verify(accountActionMail).enqueue(mailCaptor.capture());
        AccountActionMail mail = mailCaptor.getValue();
        assertThat(mail.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(mail.userId()).isEqualTo(USER_ID);
        assertThat(mail.recipient()).isEqualTo(EMAIL);
        assertThat(mail.purpose()).isEqualTo(
            AccountActionCredentialPurpose.PASSWORD_RECOVERY
        );
        assertThat(mail.confirmationLink()).isEqualTo(CONFIRMATION_LINK);
        assertThat(mail.toString())
            .isEqualTo("AccountActionMail[redacted]")
            .doesNotContain(CREDENTIAL);
    }
}
