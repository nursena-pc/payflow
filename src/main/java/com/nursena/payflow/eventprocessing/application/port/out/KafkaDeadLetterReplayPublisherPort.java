package com.nursena.payflow.eventprocessing.application.port.out;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;

public interface KafkaDeadLetterReplayPublisherPort {

    void publish(
        KafkaDeadLetterRecord record
    );
}
