package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "PasswordRecoveryConfirmRequest",
    description =
        "Opaque single-use credential and replacement "
            + "password used to recover account access."
)
public record PasswordRecoveryConfirmRequest(

    @Schema(
        description = "Opaque password-recovery credential.",
        example =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(
        message =
            "Password recovery credential is required."
    )
    @Size(
        max = 256,
        message =
            "Password recovery credential is invalid."
    )
    String credential,

    @Schema(
        description =
            "Replacement password between 12 and 72 characters.",
        example = "ReplacementPassword123!",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(message = "New password is required.")
    @Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message =
            "New password must be between 12 and 72 characters."
    )
    String newPassword
) {

    @Override
    public String toString() {
        return "PasswordRecoveryConfirmRequest[redacted]";
    }
}
