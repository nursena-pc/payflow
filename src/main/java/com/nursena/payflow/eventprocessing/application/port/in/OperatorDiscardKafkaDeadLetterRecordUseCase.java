package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.OperatorDiscardKafkaDeadLetterRecordCommand;

public interface OperatorDiscardKafkaDeadLetterRecordUseCase {

    DiscardKafkaDeadLetterRecordResult discard(
        OperatorDiscardKafkaDeadLetterRecordCommand
            command
    );
}
