package com.nursena.payflow.eventprocessing.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterRecordPage;

public interface KafkaDeadLetterQueryPort {

    KafkaDeadLetterRecordPage findPage(
        int page,
        int size,
        KafkaDeadLetterRecordFilter filter
    );

    Optional<KafkaDeadLetterRecordDetails>
    findById(
        UUID recordId
    );
}
