package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterCommand;
import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterResult;

public interface RecordKafkaDeadLetterUseCase {

    RecordKafkaDeadLetterResult record(
        RecordKafkaDeadLetterCommand command
    );
}
