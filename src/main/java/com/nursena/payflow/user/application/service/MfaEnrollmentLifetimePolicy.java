package com.nursena.payflow.user.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class MfaEnrollmentLifetimePolicy {

    private final Duration ttl;

    public MfaEnrollmentLifetimePolicy(Duration ttl) {
        this.ttl = Objects.requireNonNull(
            ttl,
            "ttl must not be null"
        );

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                "MFA enrollment TTL must be positive"
            );
        }
    }

    public Instant expiresAt(Instant issuedAt) {
        return Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        ).plus(ttl);
    }
}
