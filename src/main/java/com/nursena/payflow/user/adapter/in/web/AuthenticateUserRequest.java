package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    name = "AuthenticateUserRequest",
    description = "Credentials used to authenticate a user."
)
public record AuthenticateUserRequest(

    @Schema(
        description = "Registered user email address.",
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
        description = "User password.",
        example = "StrongPassword123!",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(message = "Password is required.")
    @Size(
        max = 72,
        message =
            "Password must not exceed 72 characters."
    )
    String password
) {
}
