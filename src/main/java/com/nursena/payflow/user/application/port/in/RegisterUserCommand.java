package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record RegisterUserCommand(
    String email,
    String rawPassword
) {

    public RegisterUserCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");

        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }
    }
}
