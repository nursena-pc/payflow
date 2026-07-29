package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record RotateRefreshCredentialsCommand(
    String refreshToken
) {

    public RotateRefreshCredentialsCommand {
        Objects.requireNonNull(
            refreshToken,
            "refreshToken must not be null"
        );

        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                "refreshToken must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "RotateRefreshCredentialsCommand[redacted]";
    }
}
