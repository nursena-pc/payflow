package com.nursena.payflow.user.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class MfaLoginChallengeLifetimePolicy {

    private final Duration ttl;
    private final int maxAttempts;

    public MfaLoginChallengeLifetimePolicy(Duration ttl, int maxAttempts) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                "maxAttempts must be between 1 and 10"
            );
        }
        this.maxAttempts = maxAttempts;
    }

    public Instant expiresAt(Instant issuedAt) {
        return Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        ).plus(ttl);
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
