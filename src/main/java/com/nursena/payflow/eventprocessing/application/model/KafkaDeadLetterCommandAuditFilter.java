package com.nursena.payflow.eventprocessing.application.model;

import java.util.UUID;

public record KafkaDeadLetterCommandAuditFilter(
    UUID commandId,
    UUID operatorId,
    UUID deadLetterRecordId,
    KafkaDeadLetterCommandType commandType,
    KafkaDeadLetterCommandAuditStage stage,
    KafkaDeadLetterCommandAuditOutcome outcome
) {

    public static KafkaDeadLetterCommandAuditFilter
    unfiltered() {
        return new KafkaDeadLetterCommandAuditFilter(
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
