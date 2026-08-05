package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "EmailVerificationRequest",
    description =
        "Normalized identity for a generic email-"
            + "verification request."
)
public record EmailVerificationRequest(

    @Schema(
        description = "User email address.",
        example = "nursena@example.com",
        format = "email"
    )
    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be valid.")
    @Size(
        max = 320,
        message =
            "Email must not exceed 320 characters."
    )
    String email
) {

    @Override
    public String toString() {
        return "EmailVerificationRequest[redacted]";
    }
}
