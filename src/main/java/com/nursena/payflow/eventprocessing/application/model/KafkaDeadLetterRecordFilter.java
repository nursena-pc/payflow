package com.nursena.payflow.eventprocessing.application.model;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;

public record KafkaDeadLetterRecordFilter(
    KafkaDeadLetterRecordStatus status
) {

    public static KafkaDeadLetterRecordFilter
    unfiltered() {
        return new KafkaDeadLetterRecordFilter(
            null
        );
    }
}
