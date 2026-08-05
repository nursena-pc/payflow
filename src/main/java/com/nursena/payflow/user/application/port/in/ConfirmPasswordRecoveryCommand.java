package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record ConfirmPasswordRecoveryCommand(
    String credential,
    String rawNewPassword
) {

    public ConfirmPasswordRecoveryCommand {
        Objects.requireNonNull(
            credential,
            "credential must not be null"
        );
        Objects.requireNonNull(
            rawNewPassword,
            "rawNewPassword must not be null"
        );

        if (credential.isBlank()) {
            throw new IllegalArgumentException(
                "credential must not be blank"
            );
        }

        if (rawNewPassword.isBlank()) {
            throw new IllegalArgumentException(
                "rawNewPassword must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "ConfirmPasswordRecoveryCommand[redacted]";
    }
}
