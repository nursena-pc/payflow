package com.nursena.payflow.eventprocessing.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;

public interface KafkaDeadLetterReplayRepositoryPort {

    ClaimKafkaDeadLetterRecordResult tryClaim(
        UUID recordId,
        String workerId,
        Instant claimedAt,
        Duration leaseDuration,
        int maxReplayAttempts
    );
}
