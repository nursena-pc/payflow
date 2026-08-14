package com.nursena.payflow.abuseprotection.application.policy;

import java.time.Duration;
import java.util.Objects;

public record AbuseProtectionPolicy(
    boolean enabled,
    Duration window,
    int identityLimit,
    int clientLimit,
    AbuseProtectionFailureMode dependencyFailureMode
) {

    private static final Duration MINIMUM_WINDOW =
        Duration.ofSeconds(1);

    private static final Duration MAXIMUM_WINDOW =
        Duration.ofDays(1);

    private static final int MAXIMUM_LIMIT =
        1_000_000;

    public AbuseProtectionPolicy {
        Objects.requireNonNull(
            window,
            "window must not be null"
        );

        Objects.requireNonNull(
            dependencyFailureMode,
            "dependencyFailureMode must not be null"
        );

        if (window.compareTo(MINIMUM_WINDOW) < 0) {
            throw new IllegalArgumentException(
                "window must be at least one second"
            );
        }

        if (window.compareTo(MAXIMUM_WINDOW) > 0) {
            throw new IllegalArgumentException(
                "window must not exceed one day"
            );
        }

        requireBoundedLimit(
            identityLimit,
            "identityLimit"
        );

        requireBoundedLimit(
            clientLimit,
            "clientLimit"
        );
    }

    private static void requireBoundedLimit(
        int value,
        String propertyName
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                propertyName + " must be positive"
            );
        }

        if (value > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                propertyName
                    + " must not exceed "
                    + MAXIMUM_LIMIT
            );
        }
    }
}
