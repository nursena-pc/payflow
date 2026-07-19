package com.nursena.payflow.outbox.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;

public interface OutboxEventClaimPort {

    List<OutboxEvent> claimAvailable(
        String publisherId,
        Instant claimedAt,
        Duration leaseDuration,
        int batchSize
    );
}
