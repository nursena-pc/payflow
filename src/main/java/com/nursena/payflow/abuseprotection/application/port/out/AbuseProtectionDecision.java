package com.nursena.payflow.abuseprotection.application.port.out;

import java.time.Duration;
import java.util.Objects;

public record AbuseProtectionDecision(
    AbuseProtectionDimension blockedDimension,
    Duration retryAfter
) {

    public AbuseProtectionDecision {
        Objects.requireNonNull(
            blockedDimension,
            "blockedDimension must not be null"
        );

        Objects.requireNonNull(
            retryAfter,
            "retryAfter must not be null"
        );

        if (blockedDimension == AbuseProtectionDimension.NONE) {
            if (!retryAfter.isZero()) {
                throw new IllegalArgumentException(
                    "allowed decision retryAfter must be zero"
                );
            }
        } else if (
            retryAfter.isZero()
                || retryAfter.isNegative()
        ) {
            throw new IllegalArgumentException(
                "blocked decision retryAfter must be positive"
            );
        }
    }

    public static AbuseProtectionDecision allowed() {
        return new AbuseProtectionDecision(
            AbuseProtectionDimension.NONE,
            Duration.ZERO
        );
    }

    public static AbuseProtectionDecision blocked(
        AbuseProtectionDimension dimension,
        Duration retryAfter
    ) {
        if (dimension == AbuseProtectionDimension.NONE) {
            throw new IllegalArgumentException(
                "blocked dimension must not be NONE"
            );
        }

        return new AbuseProtectionDecision(
            dimension,
            retryAfter
        );
    }

    public boolean isAllowed() {
        return blockedDimension
            == AbuseProtectionDimension.NONE;
    }
}
