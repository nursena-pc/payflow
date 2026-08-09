package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

public record MfaChallengeRequiredResult(
    String challengeToken,
    Instant expiresAt
) implements AuthenticateUserResult {

    public MfaChallengeRequiredResult {
        Objects.requireNonNull(
            challengeToken,
            "challengeToken must not be null"
        );
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        if (challengeToken.isBlank()) {
            throw new IllegalArgumentException(
                "challengeToken must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "MfaChallengeRequiredResult[redacted]";
    }
}
