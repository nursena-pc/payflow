package com.nursena.payflow.user.application.service;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

record PreparedPasswordRecovery(
    URI confirmationLink,
    Instant expiresAt
) {

    PreparedPasswordRecovery {
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
        return "PreparedPasswordRecovery[redacted]";
    }
}
