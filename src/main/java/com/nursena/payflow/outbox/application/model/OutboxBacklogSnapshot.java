package com.nursena.payflow.outbox.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record OutboxBacklogSnapshot(
    long eventCount,
    Optional<Instant> oldestCreatedAt
) {

    public OutboxBacklogSnapshot {
        if (eventCount < 0) {
            throw new IllegalArgumentException(
                "eventCount must not be negative."
            );
        }

        oldestCreatedAt =
            Objects.requireNonNull(
                oldestCreatedAt,
                "oldestCreatedAt must not be null"
            );

        if (
            eventCount == 0
                && oldestCreatedAt.isPresent()
        ) {
            throw new IllegalArgumentException(
                "Empty backlog must not have "
                    + "an oldest event."
            );
        }

        if (
            eventCount > 0
                && oldestCreatedAt.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Non-empty backlog must have "
                    + "an oldest event."
            );
        }
    }

    public static OutboxBacklogSnapshot empty() {
        return new OutboxBacklogSnapshot(
            0,
            Optional.empty()
        );
    }
}
