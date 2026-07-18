package com.nursena.payflow.outbox.application.port.in;

import java.time.Duration;
import java.util.Objects;

public record PublishOutboxEventsCommand(
    String publisherId,
    int batchSize,
    Duration leaseDuration
) {

    private static final int
        MAX_PUBLISHER_ID_LENGTH = 200;

    public PublishOutboxEventsCommand {
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

        Objects.requireNonNull(
            leaseDuration,
            "leaseDuration must not be null"
        );

        if (
            leaseDuration.isZero()
                || leaseDuration.isNegative()
        ) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive."
            );
        }
    }
}
