package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.application.port.out.AccountActionMailPort;
import com.nursena.payflow.user.application.port.out.EmailVerificationLinkPort;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationPreparationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "ee9acd5d-620f-4876-913d-ffb85e673c6a"
    );
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "fa69f125-7e1b-4879-831b-81fdf241b365"
    );
    private static final EmailAddress EMAIL =
        EmailAddress.of("nursena@example.com");
    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-08-06T12:00:00Z");
    private static final URI CONFIRMATION_LINK = URI.create(
        "https://app.payflow.local/verify-email?token=" + CREDENTIAL
    );

    @Mock
    private AccountActionCredentialIssuer credentialIssuer;
    @Mock
    private EmailVerificationLinkPort verificationLink;
    @Mock
    private AccountActionMailPort accountActionMail;

    private EmailVerificationPreparationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationPreparationService(
            credentialIssuer,
            verificationLink,
            accountActionMail
        );
    }

    @Test
    void shouldPrepareAndEnqueueConfiguredLink() {
        when(credentialIssuer.issue(
            USER_ID,
            AccountActionCredentialPurpose.EMAIL_VERIFICATION
        )).thenReturn(
            new IssuedAccountActionCredential(
                CREDENTIAL_ID,
                CREDENTIAL,
                EXPIRES_AT
            )
        );
        when(verificationLink.build(CREDENTIAL))
            .thenReturn(CONFIRMATION_LINK);

        PreparedEmailVerification result = service.prepare(
            USER_ID,
            EMAIL
        );

        assertThat(result.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(result.confirmationLink()).isEqualTo(CONFIRMATION_LINK);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.toString())
            .isEqualTo("PreparedEmailVerification[redacted]")
            .doesNotContain(CREDENTIAL);

        ArgumentCaptor<AccountActionMail> mailCaptor =
            ArgumentCaptor.forClass(AccountActionMail.class);
        verify(accountActionMail).enqueue(mailCaptor.capture());
        AccountActionMail mail = mailCaptor.getValue();
        assertThat(mail.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(mail.userId()).isEqualTo(USER_ID);
        assertThat(mail.recipient()).isEqualTo(EMAIL);
        assertThat(mail.purpose()).isEqualTo(
            AccountActionCredentialPurpose.EMAIL_VERIFICATION
        );
        assertThat(mail.confirmationLink()).isEqualTo(CONFIRMATION_LINK);
        assertThat(mail.toString())
            .isEqualTo("AccountActionMail[redacted]")
            .doesNotContain(CREDENTIAL);
    }
}
