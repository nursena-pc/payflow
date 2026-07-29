package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "RotateRefreshCredentialsResponse",
    description =
        "New access and refresh credentials returned "
            + "after successful refresh-token rotation."
)
public record RotateRefreshCredentialsResponse(

    @Schema(
        description = "RSA-signed JWT access token.",
        example = "eyJhbGciOiJSUzI1NiJ9..."
    )
    String accessToken,

    @Schema(
        description =
            "Authentication scheme used with the access token.",
        example = "Bearer"
    )
    String tokenType,

    @Schema(
        description = "Access-token expiration time.",
        example = "2026-07-28T12:15:00Z",
        format = "date-time"
    )
    Instant expiresAt,

    @Schema(
        description =
            "New opaque refresh token replacing the "
                + "presented refresh token.",
        example =
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8"
    )
    String refreshToken,

    @Schema(
        description = "New refresh-token expiration time.",
        example = "2026-08-04T12:00:00Z",
        format = "date-time"
    )
    Instant refreshTokenExpiresAt
) {

    public RotateRefreshCredentialsResponse {
        Objects.requireNonNull(
            accessToken,
            "accessToken must not be null"
        );
        Objects.requireNonNull(
            tokenType,
            "tokenType must not be null"
        );
        Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );
        Objects.requireNonNull(
            refreshToken,
            "refreshToken must not be null"
        );
        Objects.requireNonNull(
            refreshTokenExpiresAt,
            "refreshTokenExpiresAt must not be null"
        );

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException(
                "accessToken must not be blank"
            );
        }

        if (tokenType.isBlank()) {
            throw new IllegalArgumentException(
                "tokenType must not be blank"
            );
        }

        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                "refreshToken must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "RotateRefreshCredentialsResponse[redacted]";
    }
}
