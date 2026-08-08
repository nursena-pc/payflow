package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record BeginMfaEnrollmentCommand(
    UUID userId,
    String currentPassword
) {
    public BeginMfaEnrollmentCommand {
        Objects.requireNonNull(userId, "userId must not be null");
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException(
                "currentPassword must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "BeginMfaEnrollmentCommand[redacted]";
    }
}
