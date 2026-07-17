package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AuthenticateUserResponse",
    description =
        "JWT access token returned after successful authentication."
)
public record AuthenticateUserResponse(

    @Schema(
        description = "RSA-signed JWT access token.",
        example = "eyJhbGciOiJSUzI1NiJ9..."
    )
    String accessToken,

    @Schema(
        description =
            "Authentication scheme used with the token.",
        example = "Bearer"
    )
    String tokenType,

    @Schema(
        description = "Access-token expiration time.",
        example = "2026-07-17T12:15:00Z",
        format = "date-time"
    )
    Instant expiresAt
) {
}
