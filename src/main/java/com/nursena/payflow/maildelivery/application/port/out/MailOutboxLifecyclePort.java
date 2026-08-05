package com.nursena.payflow.maildelivery.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface MailOutboxLifecyclePort {

    void markSent(
        UUID messageId,
        String workerId,
        Instant deliveredAt
    );

    void scheduleRetry(
        UUID messageId,
        String workerId,
        Instant failedAt,
        Instant nextAvailableAt,
        String error
    );

    void markFailed(
        UUID messageId,
        String workerId,
        Instant failedAt,
        String error
    );
}
