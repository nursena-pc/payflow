package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

import com.nursena.payflow.user.application.port.in.GetMfaStatusResult;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record MfaStatusResponse(
    MfaLifecycleState state,
    Instant enrollmentExpiresAt,
    Instant activatedAt
) {
    static MfaStatusResponse from(GetMfaStatusResult result) {
        return new MfaStatusResponse(
            result.state(),
            result.enrollmentExpiresAt(),
            result.activatedAt()
        );
    }
}
