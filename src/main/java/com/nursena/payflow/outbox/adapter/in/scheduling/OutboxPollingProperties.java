package com.nursena.payflow.outbox.adapter.in.scheduling;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.outbox.polling"
)
public record OutboxPollingProperties(
    boolean enabled,
    String publisherId,
    int batchSize,
    Duration leaseDuration,
    Duration fixedDelay,
    Duration initialDelay
) {

    private static final int
        MAX_PUBLISHER_ID_LENGTH = 200;

    public OutboxPollingProperties {
        if (
            publisherId == null
                || publisherId.isBlank()
        ) {
            throw new IllegalArgumentException(
                "publisherId must not be blank."
            );
        }

        if (
            publisherId.length()
                > MAX_PUBLISHER_ID_LENGTH
        ) {
            throw new IllegalArgumentException(
                "publisherId must not exceed "
                    + MAX_PUBLISHER_ID_LENGTH
                    + " characters."
            );
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive."
            );
        }

        leaseDuration =
            requirePositive(
                leaseDuration,
                "leaseDuration"
            );

        fixedDelay =
            requirePositive(
                fixedDelay,
                "fixedDelay"
            );

        Objects.requireNonNull(
            initialDelay,
            "initialDelay must not be null"
        );

        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException(
                "initialDelay must not be negative."
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
