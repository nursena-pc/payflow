package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "EmailVerificationConfirmRequest",
    description =
        "Opaque single-use credential used to confirm "
            + "email ownership."
)
public record EmailVerificationConfirmRequest(

    @Schema(
        description =
            "Opaque email-verification credential.",
        example =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(
        message =
            "Email verification credential is required."
    )
    @Size(
        max = 256,
        message =
            "Email verification credential is invalid."
    )
    String credential
) {

    @Override
    public String toString() {
        return "EmailVerificationConfirmRequest[redacted]";
    }
}
