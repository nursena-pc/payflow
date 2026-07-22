package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;
import java.util.UUID;

public record DiscardKafkaDeadLetterRecordCommand(
    UUID recordId
) {

    public DiscardKafkaDeadLetterRecordCommand {
        recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );
    }
}
