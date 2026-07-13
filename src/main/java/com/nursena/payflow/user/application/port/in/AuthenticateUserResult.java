package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

public record AuthenticateUserResult(
    String accessToken,
    Instant expiresAt
) {

    public AuthenticateUserResult {
        Objects.requireNonNull(
            accessToken,
            "accessToken must not be null"
        );
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException(
                "accessToken must not be blank"
            );
        }
    }
}
