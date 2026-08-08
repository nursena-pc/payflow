package com.nursena.payflow.user.domain.model;

import java.util.Arrays;

public final class ProtectedMfaSecret {

    private final byte[] value;

    private ProtectedMfaSecret(byte[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    public static ProtectedMfaSecret of(byte[] value) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException(
                "protected MFA secret must not be empty"
            );
        }

        return new ProtectedMfaSecret(value);
    }

    public byte[] value() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public String toString() {
        return "ProtectedMfaSecret[REDACTED]";
    }
}
