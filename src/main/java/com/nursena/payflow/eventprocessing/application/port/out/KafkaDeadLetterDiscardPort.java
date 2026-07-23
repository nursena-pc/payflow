package com.nursena.payflow.eventprocessing.application.port.out;

import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;

public interface KafkaDeadLetterDiscardPort {

    DiscardKafkaDeadLetterRecordResult discard(
        UUID recordId
    );
}
