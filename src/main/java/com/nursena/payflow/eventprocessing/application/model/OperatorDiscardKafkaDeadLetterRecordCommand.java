package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;
import java.util.UUID;

public record OperatorDiscardKafkaDeadLetterRecordCommand(
    UUID operatorId,
    UUID recordId
) {

    public OperatorDiscardKafkaDeadLetterRecordCommand {
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
