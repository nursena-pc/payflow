package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;

public record GetCurrentUserProfileResult(
    UUID id,
    String email,
    UserRole role,
    UserStatus status,
    Instant createdAt
) {

    public GetCurrentUserProfileResult {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        if (email.isBlank()) {
            throw new IllegalArgumentException(
                "email must not be blank"
            );
        }
    }
}
