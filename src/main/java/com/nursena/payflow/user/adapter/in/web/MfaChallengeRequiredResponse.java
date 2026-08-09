package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MfaChallengeRequiredResponse")
public record MfaChallengeRequiredResponse(
    @Schema(example = "MFA_REQUIRED")
    String authenticationStatus,
    @Schema(description = "Opaque, short-lived MFA login challenge.")
    String challengeToken,
    @Schema(description = "Challenge expiration time.")
    Instant expiresAt
) {
    public MfaChallengeRequiredResponse {
        Objects.requireNonNull(authenticationStatus);
        Objects.requireNonNull(challengeToken);
        Objects.requireNonNull(expiresAt);
    }

    @Override
    public String toString() {
        return "MfaChallengeRequiredResponse[redacted]";
    }
}
