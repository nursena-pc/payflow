package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;

public interface DiscardKafkaDeadLetterRecordUseCase {

    DiscardKafkaDeadLetterRecordResult discard(
        DiscardKafkaDeadLetterRecordCommand command
    );
}
