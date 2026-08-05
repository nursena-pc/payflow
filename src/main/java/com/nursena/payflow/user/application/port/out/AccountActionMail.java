package com.nursena.payflow.user.application.port.out;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.EmailAddress;

public record AccountActionMail(
    UUID credentialId,
    UUID userId,
    EmailAddress recipient,
    AccountActionCredentialPurpose purpose,
    URI confirmationLink,
    Instant expiresAt
) {

    public AccountActionMail {
        Objects.requireNonNull(
            credentialId,
            "credentialId must not be null"
        );
        Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        Objects.requireNonNull(
            recipient,
            "recipient must not be null"
        );
        Objects.requireNonNull(
            purpose,
            "purpose must not be null"
        );
        Objects.requireNonNull(
            confirmationLink,
            "confirmationLink must not be null"
        );
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );

        if (!confirmationLink.isAbsolute()) {
            throw new IllegalArgumentException(
                "confirmationLink must be absolute"
            );
        }
    }

    @Override
    public String toString() {
        return "AccountActionMail[redacted]";
    }
}
