package com.nursena.payflow.user.application.port.out;

import java.util.Arrays;
import java.util.Objects;

public final class GeneratedTotpSecret {

    private final byte[] value;
    private final String base32;

    public GeneratedTotpSecret(
        byte[] value,
        String base32
    ) {
        if (value == null || value.length < 20) {
            throw new IllegalArgumentException(
                "TOTP secret must contain at least 160 bits"
            );
        }

        this.value = Arrays.copyOf(
            value,
            value.length
        );
        this.base32 = Objects.requireNonNull(
            base32,
            "base32 must not be null"
        );

        if (base32.isBlank()) {
            throw new IllegalArgumentException(
                "base32 must not be blank"
            );
        }
    }

    public byte[] value() {
        return Arrays.copyOf(
            value,
            value.length
        );
    }

    public String base32() {
        return base32;
    }

    @Override
    public String toString() {
        return "GeneratedTotpSecret[REDACTED]";
    }
}
