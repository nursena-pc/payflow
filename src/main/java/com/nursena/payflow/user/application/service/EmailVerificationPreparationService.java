package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.application.port.out.AccountActionMailPort;
import com.nursena.payflow.user.application.port.out.EmailVerificationLinkPort;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmailVerificationPreparationService {

    private final AccountActionCredentialIssuer credentialIssuer;
    private final EmailVerificationLinkPort verificationLink;
    private final AccountActionMailPort accountActionMail;

    EmailVerificationPreparationService(
        AccountActionCredentialIssuer credentialIssuer,
        EmailVerificationLinkPort verificationLink,
        AccountActionMailPort accountActionMail
    ) {
        this.credentialIssuer = Objects.requireNonNull(
            credentialIssuer,
            "credentialIssuer must not be null"
        );
        this.verificationLink = Objects.requireNonNull(
            verificationLink,
            "verificationLink must not be null"
        );
        this.accountActionMail = Objects.requireNonNull(
            accountActionMail,
            "accountActionMail must not be null"
        );
    }

    @Transactional
    public PreparedEmailVerification prepare(
        UUID userId,
        EmailAddress recipient
    ) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        EmailAddress checkedRecipient = Objects.requireNonNull(
            recipient,
            "recipient must not be null"
        );

        IssuedAccountActionCredential issued =
            credentialIssuer.issue(
                checkedUserId,
                AccountActionCredentialPurpose.EMAIL_VERIFICATION
            );

        URI confirmationLink = verificationLink.build(
            issued.value()
        );

        accountActionMail.enqueue(
            new AccountActionMail(
                issued.credentialId(),
                checkedUserId,
                checkedRecipient,
                AccountActionCredentialPurpose.EMAIL_VERIFICATION,
                confirmationLink,
                issued.expiresAt()
            )
        );

        return new PreparedEmailVerification(
            issued.credentialId(),
            confirmationLink,
            issued.expiresAt()
        );
    }
}
