package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;
import java.util.UUID;

public record ClaimKafkaDeadLetterRecordCommand(
    UUID recordId
) {

    public ClaimKafkaDeadLetterRecordCommand {
        recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );
    }
}
