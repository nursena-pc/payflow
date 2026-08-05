package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

record PreparedEmailVerification(
    URI confirmationLink,
    Instant expiresAt
) {

    PreparedEmailVerification {
        Objects.requireNonNull(
            confirmationLink,
            "confirmationLink must not be null"
        );
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );
    }

    @Override
    public String toString() {
        return "PreparedEmailVerification[redacted]";
    }
}
