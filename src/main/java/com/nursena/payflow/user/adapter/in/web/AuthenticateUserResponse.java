package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "AuthenticateUserResponse",
    description =
        "Access and refresh credentials returned after "
            + "successful authentication."
)
public record AuthenticateUserResponse(

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
        example = "2026-07-17T12:15:00Z",
        format = "date-time"
    )
    Instant expiresAt,

    @Schema(
        description =
            "Opaque refresh token used to obtain new credentials.",
        example =
            "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
    )
    String refreshToken,

    @Schema(
        description = "Refresh-token expiration time.",
        example = "2026-07-24T12:00:00Z",
        format = "date-time"
    )
    Instant refreshTokenExpiresAt
) {

    public AuthenticateUserResponse {
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
        return "AuthenticateUserResponse[redacted]";
    }
}
