package com.nursena.payflow.user.application.port.out;

import java.time.Duration;
import java.util.Objects;

public record LoginRateLimitDecision(
    LoginRateLimitDimension blockedDimension,
    Duration retryAfter
) {

    public LoginRateLimitDecision {
        Objects.requireNonNull(
            blockedDimension,
            "blockedDimension must not be null"
        );

        Objects.requireNonNull(
            retryAfter,
            "retryAfter must not be null"
        );

        if (blockedDimension == LoginRateLimitDimension.NONE) {
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

    public static LoginRateLimitDecision allowed() {
        return new LoginRateLimitDecision(
            LoginRateLimitDimension.NONE,
            Duration.ZERO
        );
    }

    public static LoginRateLimitDecision blocked(
        LoginRateLimitDimension dimension,
        Duration retryAfter
    ) {
        if (dimension == LoginRateLimitDimension.NONE) {
            throw new IllegalArgumentException(
                "blocked dimension must not be NONE"
            );
        }

        return new LoginRateLimitDecision(
            dimension,
            retryAfter
        );
    }

    public boolean isAllowed() {
        return blockedDimension
            == LoginRateLimitDimension.NONE;
    }
}
