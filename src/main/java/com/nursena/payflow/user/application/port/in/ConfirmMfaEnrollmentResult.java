package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record ConfirmMfaEnrollmentResult(
    MfaLifecycleState state,
    Instant activatedAt,
    List<String> recoveryCodes
) {
    public ConfirmMfaEnrollmentResult {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(
            activatedAt,
            "activatedAt must not be null"
        );
        recoveryCodes = List.copyOf(Objects.requireNonNull(
            recoveryCodes,
            "recoveryCodes must not be null"
        ));

        if (recoveryCodes.isEmpty()) {
            throw new IllegalArgumentException(
                "recoveryCodes must not be empty"
            );
        }
    }

    @Override
    public String toString() {
        return "ConfirmMfaEnrollmentResult[redacted]";
    }
}
