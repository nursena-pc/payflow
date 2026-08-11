package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record StepUpGrantResponse(
    @Schema(
        description = "Short-lived opaque step-up credential. Returned once.",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    String grantToken,
    @Schema(description = "Exact purpose bound into the grant.")
    String purpose,
    @Schema(description = "UTC instant after which the grant is invalid.")
    Instant expiresAt
) {
    static StepUpGrantResponse from(IssueStepUpGrantResult result) {
        return new StepUpGrantResponse(
            result.grantToken(),
            result.purpose(),
            result.expiresAt()
        );
    }

    @Override
    public String toString() {
        return "StepUpGrantResponse[purpose="
            + purpose
            + ", expiresAt="
            + expiresAt
            + ", grantToken=redacted]";
    }
}
