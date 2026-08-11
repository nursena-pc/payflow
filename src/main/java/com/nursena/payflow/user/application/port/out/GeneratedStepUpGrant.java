package com.nursena.payflow.user.application.port.out;

import java.util.Objects;

public record GeneratedStepUpGrant(String value) {
    public GeneratedStepUpGrant {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return "GeneratedStepUpGrant[redacted]";
    }
}
