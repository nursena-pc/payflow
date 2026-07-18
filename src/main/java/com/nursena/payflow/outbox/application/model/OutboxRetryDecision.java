package com.nursena.payflow.outbox.application.model;

import java.time.Instant;
import java.util.Objects;

public record OutboxRetryDecision(
    boolean shouldRetry,
    Instant nextAvailableAt
) {

    public OutboxRetryDecision {
        if (
            shouldRetry
                && nextAvailableAt == null
        ) {
            throw new IllegalArgumentException(
                "Retry decision must have "
                    + "nextAvailableAt."
            );
        }

        if (
            !shouldRetry
                && nextAvailableAt != null
        ) {
            throw new IllegalArgumentException(
                "Terminal failure must not have "
                    + "nextAvailableAt."
            );
        }
    }

    public static OutboxRetryDecision retryAt(
        Instant nextAvailableAt
    ) {
        return new OutboxRetryDecision(
            true,
            Objects.requireNonNull(
                nextAvailableAt,
                "nextAvailableAt must not be null"
            )
        );
    }

    public static OutboxRetryDecision
    terminalFailure() {
        return new OutboxRetryDecision(
            false,
            null
        );
    }
}
