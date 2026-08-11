package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueStepUpGrantRequest(
    @Schema(
        description = "Exact stable step-up purpose value.",
        example = "mfa-disable"
    )
    @NotBlank @Size(max = 64) String purpose,
    @Schema(
        description = "Six-digit TOTP or one unused MFA recovery code.",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank @Size(max = 64) String code
) {
    @Override
    public String toString() {
        return "IssueStepUpGrantRequest[purpose="
            + purpose
            + ", code=redacted]";
    }
}
