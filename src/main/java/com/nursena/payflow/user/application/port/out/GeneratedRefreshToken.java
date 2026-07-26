package com.nursena.payflow.user.application.port.out;

import java.util.Objects;

public record GeneratedRefreshToken(
    String value
) {

    public GeneratedRefreshToken {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                "value must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "GeneratedRefreshToken[redacted]";
    }
}
