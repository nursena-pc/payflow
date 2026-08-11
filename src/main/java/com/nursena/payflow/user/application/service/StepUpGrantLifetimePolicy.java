package com.nursena.payflow.user.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class StepUpGrantLifetimePolicy {

    private final Duration ttl;

    public StepUpGrantLifetimePolicy(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    public Instant expiresAt(Instant issuedAt) {
        return Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        ).plus(ttl);
    }
}
