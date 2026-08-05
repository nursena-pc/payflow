package com.nursena.payflow.user.application.port.out;

import java.util.Objects;

public record GeneratedAccountActionCredential(
    String value
) {

    public GeneratedAccountActionCredential {
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
        return "GeneratedAccountActionCredential[redacted]";
    }
}
