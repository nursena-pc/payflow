package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .PasswordRecoveryLinkPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation
    .Transactional;

@Component
class PasswordRecoveryPreparationService {

    private final AccountActionCredentialIssuer
        credentialIssuer;
    private final PasswordRecoveryLinkPort recoveryLink;

    PasswordRecoveryPreparationService(
        AccountActionCredentialIssuer credentialIssuer,
        PasswordRecoveryLinkPort recoveryLink
    ) {
        this.credentialIssuer = Objects.requireNonNull(
            credentialIssuer,
            "credentialIssuer must not be null"
        );
        this.recoveryLink = Objects.requireNonNull(
            recoveryLink,
            "recoveryLink must not be null"
        );
    }

    @Transactional
    public PreparedPasswordRecovery prepare(UUID userId) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );

        IssuedAccountActionCredential issued =
            credentialIssuer.issue(
                checkedUserId,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            );

        URI confirmationLink = recoveryLink.build(
            issued.value()
        );

        return new PreparedPasswordRecovery(
            confirmationLink,
            issued.expiresAt()
        );
    }
}
