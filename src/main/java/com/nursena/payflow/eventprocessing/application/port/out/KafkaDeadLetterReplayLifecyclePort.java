package com.nursena.payflow.eventprocessing.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface KafkaDeadLetterReplayLifecyclePort {

    boolean tryMarkReplayed(
        UUID recordId,
        String workerId,
        Instant completedAt
    );

    boolean tryMarkReplayFailed(
        UUID recordId,
        String workerId,
        Instant failedAt,
        String error
    );
}
