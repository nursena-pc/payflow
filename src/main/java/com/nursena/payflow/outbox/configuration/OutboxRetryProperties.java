package com.nursena.payflow.outbox.configuration;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.outbox.retry"
)
public record OutboxRetryProperties(
    int maxAttempts,
    Duration initialDelay,
    Duration maximumDelay
) {

    public OutboxRetryProperties {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "maxAttempts must be positive."
            );
        }

        initialDelay =
            requirePositive(
                initialDelay,
                "initialDelay"
            );

        maximumDelay =
            requirePositive(
                maximumDelay,
                "maximumDelay"
            );

        if (
            maximumDelay.compareTo(initialDelay)
                < 0
        ) {
            throw new IllegalArgumentException(
                "maximumDelay must not be less "
                    + "than initialDelay."
            );
        }
    }

    private static Duration requirePositive(
        Duration value,
        String fieldName
    ) {
        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (
            value.isZero()
                || value.isNegative()
        ) {
            throw new IllegalArgumentException(
                fieldName + " must be positive."
            );
        }

        return value;
    }
}
