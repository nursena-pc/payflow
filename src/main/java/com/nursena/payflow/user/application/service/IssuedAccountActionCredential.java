package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.Objects;

public record IssuedAccountActionCredential(
    String value,
    Instant expiresAt
) {

    public IssuedAccountActionCredential {
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
