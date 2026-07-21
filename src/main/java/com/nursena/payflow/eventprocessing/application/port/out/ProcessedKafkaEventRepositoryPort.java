package com.nursena.payflow.eventprocessing.application.port.out;

import com.nursena.payflow.eventprocessing.domain.model.ProcessedKafkaEvent;

public interface ProcessedKafkaEventRepositoryPort {

    boolean tryRecord(
        ProcessedKafkaEvent event
    );
}
