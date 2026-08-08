package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ConfirmMfaEnrollmentCommand(
    UUID userId,
    String code
) {
    public ConfirmMfaEnrollmentCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                "code must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "ConfirmMfaEnrollmentCommand[redacted]";
    }
}
