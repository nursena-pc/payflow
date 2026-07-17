package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "RegisterUserRequest",
    description = "Information required to register a user."
)
public record RegisterUserRequest(

    @Schema(
        description =
            "User email address. The application "
                + "normalizes the address before persistence.",
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
    String email,

    @Schema(
        description =
            "User password between 12 and 72 characters.",
        example = "StrongPassword123!",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(message = "Password is required.")
    @Size(
        min = 12,
        max = 72,
        message =
            "Password must be between 12 and 72 characters."
    )
    String password
) {
}
