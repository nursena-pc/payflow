package com.nursena.payflow.user.application.port.out;

import java.util.Objects;

public record GeneratedMfaRecoveryCode(String value) {

    public GeneratedMfaRecoveryCode {
        Objects.requireNonNull(value, "value must not be null");

        if (!value.matches("[A-Za-z0-9_-]{22}")) {
            throw new IllegalArgumentException(
                "value must be a canonical 128-bit Base64URL recovery code"
            );
        }
    }

    @Override
    public String toString() {
        return "GeneratedMfaRecoveryCode[redacted]";
    }
}
