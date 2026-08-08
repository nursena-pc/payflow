package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentResult;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;

public record MfaEnrollmentConfirmationResponse(
    MfaLifecycleState state,
    Instant activatedAt
) {
    static MfaEnrollmentConfirmationResponse from(
        ConfirmMfaEnrollmentResult result
    ) {
        return new MfaEnrollmentConfirmationResponse(
            result.state(),
            result.activatedAt()
        );
    }
}
