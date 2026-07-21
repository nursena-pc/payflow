package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;

public interface ReplayKafkaDeadLetterRecordUseCase {

    ReplayKafkaDeadLetterRecordResult replay(
        ReplayKafkaDeadLetterRecordCommand command
    );
}
