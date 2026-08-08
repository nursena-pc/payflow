package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record GetMfaStatusResult(
    MfaLifecycleState state,
    Instant enrollmentExpiresAt,
    Instant activatedAt
) {
    public GetMfaStatusResult {
        Objects.requireNonNull(state, "state must not be null");
    }
}
