package com.nursena.payflow.eventprocessing.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;

public interface KafkaDeadLetterReplayRepositoryPort {

    Optional<KafkaDeadLetterRecord> tryClaim(
        UUID recordId,
        String workerId,
        Instant claimedAt,
        Duration leaseDuration,
        int maxReplayAttempts
    );
}
