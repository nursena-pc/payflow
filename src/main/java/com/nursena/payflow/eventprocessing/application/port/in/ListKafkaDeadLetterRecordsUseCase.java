package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordPage;

public interface ListKafkaDeadLetterRecordsUseCase {

    KafkaDeadLetterRecordPage
    listKafkaDeadLetterRecords(
        ListKafkaDeadLetterRecordsQuery query
    );
}
