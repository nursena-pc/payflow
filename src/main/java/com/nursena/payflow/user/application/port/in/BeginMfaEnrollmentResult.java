package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record BeginMfaEnrollmentResult(
    MfaLifecycleState state,
    String secret,
    String provisioningUri,
    Instant expiresAt
) {
    public BeginMfaEnrollmentResult {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(
            provisioningUri,
            "provisioningUri must not be null"
        );
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "BeginMfaEnrollmentResult[redacted]";
    }
}
