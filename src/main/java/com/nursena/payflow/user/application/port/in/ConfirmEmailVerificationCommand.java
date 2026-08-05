package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record ConfirmEmailVerificationCommand(
    String credential
) {

    public ConfirmEmailVerificationCommand {
        Objects.requireNonNull(
            credential,
            "credential must not be null"
        );

        if (credential.isBlank()) {
            throw new IllegalArgumentException(
                "credential must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "ConfirmEmailVerificationCommand[redacted]";
    }
}
