package com.nursena.payflow.eventprocessing.application.model;

import java.time.Instant;
import java.util.Objects;

public record KafkaDeadLetterRecordDetails(
    KafkaDeadLetterRecordSummary summary,
    String exceptionMessage,
    String lastReplayError,
    Instant replayLeaseUntil
) {

    public KafkaDeadLetterRecordDetails {
        summary =
            Objects.requireNonNull(
                summary,
                "summary must not be null"
            );
    }
}
