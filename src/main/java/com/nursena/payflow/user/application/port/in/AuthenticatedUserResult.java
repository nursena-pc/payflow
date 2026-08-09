package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

public record AuthenticatedUserResult(
    String accessToken,
    Instant expiresAt,
    String refreshToken,
    Instant refreshTokenExpiresAt
) implements AuthenticateUserResult {

    public AuthenticatedUserResult {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(
            refreshTokenExpiresAt,
            "refreshTokenExpiresAt must not be null"
        );

        if (accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken must not be blank");
        }
    }

    @Override
    public String toString() {
        return "AuthenticatedUserResult[redacted]";
    }
}
