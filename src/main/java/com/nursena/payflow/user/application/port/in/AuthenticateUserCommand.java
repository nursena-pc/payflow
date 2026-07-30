
package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record AuthenticateUserCommand(
    String email,
    String rawPassword,
    String clientAddress
) {

    public AuthenticateUserCommand {
        Objects.requireNonNull(
            email,
            "email must not be null"
        );

        Objects.requireNonNull(
            rawPassword,
            "rawPassword must not be null"
        );

        Objects.requireNonNull(
            clientAddress,
            "clientAddress must not be null"
        );

        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException(
                "rawPassword must not be blank"
            );
        }

        if (clientAddress.isBlank()) {
            throw new IllegalArgumentException(
                "clientAddress must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "AuthenticateUserCommand[redacted]";
    }
}
