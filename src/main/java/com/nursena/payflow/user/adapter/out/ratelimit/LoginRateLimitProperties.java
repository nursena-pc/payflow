package com.nursena.payflow.user.adapter.out.ratelimit;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.login-rate-limit"
)
public record LoginRateLimitProperties(
    boolean enabled,
    Duration window,
    int identityLimit,
    int clientLimit
) {

    private static final Duration MINIMUM_WINDOW =
        Duration.ofSeconds(1);

    public LoginRateLimitProperties {
        Objects.requireNonNull(
            window,
            "window must not be null"
        );

        if (window.compareTo(MINIMUM_WINDOW) < 0) {
            throw new IllegalArgumentException(
                "window must be at least one second"
            );
        }

        requirePositive(
            identityLimit,
            "identityLimit"
        );

        requirePositive(
            clientLimit,
            "clientLimit"
        );
    }

    private static void requirePositive(
        int value,
        String propertyName
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                propertyName + " must be positive"
            );
        }
    }
}
