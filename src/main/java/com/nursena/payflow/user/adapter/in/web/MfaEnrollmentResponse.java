package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentResult;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record MfaEnrollmentResponse(
    MfaLifecycleState state,
    String secret,
    String provisioningUri,
    Instant expiresAt
) {
    static MfaEnrollmentResponse from(BeginMfaEnrollmentResult result) {
        return new MfaEnrollmentResponse(
            result.state(),
            result.secret(),
            result.provisioningUri(),
            result.expiresAt()
        );
    }

    @Override
    public String toString() {
        return "MfaEnrollmentResponse[redacted]";
    }
}
