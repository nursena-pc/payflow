package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record ConfirmMfaEnrollmentResult(
    MfaLifecycleState state,
    Instant activatedAt
) {
    public ConfirmMfaEnrollmentResult {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(
            activatedAt,
            "activatedAt must not be null"
        );
    }
}
