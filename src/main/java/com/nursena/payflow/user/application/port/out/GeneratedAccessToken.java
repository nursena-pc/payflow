package com.nursena.payflow.user.application.port.out;

import java.time.Instant;
import java.util.Objects;

public record GeneratedAccessToken(
    String value,
    Instant expiresAt
) {

    public GeneratedAccessToken {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                "value must not be blank"
            );
        }
    }
}
