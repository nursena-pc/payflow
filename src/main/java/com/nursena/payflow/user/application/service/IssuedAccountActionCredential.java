package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IssuedAccountActionCredential(
    UUID credentialId,
    String value,
    Instant expiresAt
) {

    public IssuedAccountActionCredential {
        Objects.requireNonNull(
            credentialId,
            "credentialId must not be null"
        );
        Objects.requireNonNull(
            value,
            "value must not be null"
        );
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                "value must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "IssuedAccountActionCredential[redacted]";
    }
}
