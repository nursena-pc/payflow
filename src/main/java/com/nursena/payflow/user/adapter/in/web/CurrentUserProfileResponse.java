package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;

public record CurrentUserProfileResponse(
    UUID id,
    String email,
    UserRole role,
    UserStatus status,
    Instant createdAt
) {

    static CurrentUserProfileResponse from(
        GetCurrentUserProfileResult result
    ) {
        return new CurrentUserProfileResponse(
            result.id(),
            result.email(),
            result.role(),
            result.status(),
            result.createdAt()
        );
    }
}
