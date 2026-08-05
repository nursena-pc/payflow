package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.application.port.out.AccountActionMailPort;
import com.nursena.payflow.user.application.port.out.PasswordRecoveryLinkPort;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PasswordRecoveryPreparationService {

    private final AccountActionCredentialIssuer credentialIssuer;
    private final PasswordRecoveryLinkPort recoveryLink;
    private final AccountActionMailPort accountActionMail;

    PasswordRecoveryPreparationService(
        AccountActionCredentialIssuer credentialIssuer,
        PasswordRecoveryLinkPort recoveryLink,
        AccountActionMailPort accountActionMail
    ) {
        this.credentialIssuer = Objects.requireNonNull(
            credentialIssuer,
            "credentialIssuer must not be null"
        );
        this.recoveryLink = Objects.requireNonNull(
            recoveryLink,
            "recoveryLink must not be null"
        );
        this.accountActionMail = Objects.requireNonNull(
            accountActionMail,
            "accountActionMail must not be null"
        );
    }

    @Transactional
    public PreparedPasswordRecovery prepare(
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
                AccountActionCredentialPurpose.PASSWORD_RECOVERY
            );

        URI confirmationLink = recoveryLink.build(
            issued.value()
        );

        accountActionMail.enqueue(
            new AccountActionMail(
                issued.credentialId(),
                checkedUserId,
                checkedRecipient,
                AccountActionCredentialPurpose.PASSWORD_RECOVERY,
                confirmationLink,
                issued.expiresAt()
            )
        );

        return new PreparedPasswordRecovery(
            issued.credentialId(),
            confirmationLink,
            issued.expiresAt()
        );
    }
}
