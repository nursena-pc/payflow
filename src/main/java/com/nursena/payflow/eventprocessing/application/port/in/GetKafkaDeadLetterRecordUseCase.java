package com.nursena.payflow.eventprocessing.application.port.in;

import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordDetails;

public interface GetKafkaDeadLetterRecordUseCase {

    KafkaDeadLetterRecordDetails
    getKafkaDeadLetterRecord(
        UUID recordId
    );
}
