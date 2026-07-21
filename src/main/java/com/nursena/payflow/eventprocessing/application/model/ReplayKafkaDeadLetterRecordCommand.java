package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;
import java.util.UUID;

public record ReplayKafkaDeadLetterRecordCommand(
    UUID recordId
) {

    public ReplayKafkaDeadLetterRecordCommand {
        recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );
    }
}
