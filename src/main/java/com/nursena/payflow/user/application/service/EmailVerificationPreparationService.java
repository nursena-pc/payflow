package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .EmailVerificationLinkPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation
    .Transactional;

@Component
class EmailVerificationPreparationService {

    private final AccountActionCredentialIssuer
        credentialIssuer;
    private final EmailVerificationLinkPort
        verificationLink;

    EmailVerificationPreparationService(
        AccountActionCredentialIssuer credentialIssuer,
        EmailVerificationLinkPort verificationLink
    ) {
        this.credentialIssuer = Objects.requireNonNull(
            credentialIssuer,
            "credentialIssuer must not be null"
        );
        this.verificationLink = Objects.requireNonNull(
            verificationLink,
            "verificationLink must not be null"
        );
    }

    @Transactional
    public PreparedEmailVerification prepare(UUID userId) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );

        IssuedAccountActionCredential issued =
            credentialIssuer.issue(
                checkedUserId,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            );

        URI confirmationLink = verificationLink.build(
            issued.value()
        );

        return new PreparedEmailVerification(
            confirmationLink,
            issued.expiresAt()
        );
    }
}
