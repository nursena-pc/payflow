package com.nursena.payflow.outbox.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;

public interface OutboxEventLifecyclePort {

    OutboxEvent markPublished(
        UUID eventId,
        String publisherId,
        Instant publishedAt
    );

    OutboxEvent scheduleRetry(
        UUID eventId,
        String publisherId,
        Instant failedAt,
        Instant nextAvailableAt,
        String error
    );

    OutboxEvent markFailed(
        UUID eventId,
        String publisherId,
        Instant failedAt,
        String error
    );
}
