package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "CurrentUserProfileResponse",
    description = "Profile information of the authenticated user."
)
public record CurrentUserProfileResponse(
    @Schema(
        description = "User identifier.",
        example = "8805681d-d537-42f2-8906-5da1f0666ab7",
        format = "uuid"
    )
    UUID id,

    @Schema(
        description = "Normalized email address.",
        example = "nursena@example.com",
        format = "email"
    )
    String email,

    @Schema(
        description = "Role assigned to the user.",
        example = "USER"
    )
    UserRole role,

    @Schema(
        description = "Current account status.",
        example = "ACTIVE"
    )
    UserStatus status,

    @Schema(
        description = "Account creation time.",
        example = "2026-07-17T12:00:00Z",
        format = "date-time"
    )
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
