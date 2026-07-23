package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;
import java.util.UUID;

public record OperatorReplayKafkaDeadLetterRecordCommand(
    UUID operatorId,
    UUID recordId
) {

    public OperatorReplayKafkaDeadLetterRecordCommand {
        operatorId =
            Objects.requireNonNull(
                operatorId,
                "operatorId must not be null"
            );
        recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );
    }
}
