package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.OperatorReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;

public interface OperatorReplayKafkaDeadLetterRecordUseCase {

    ReplayKafkaDeadLetterRecordResult replay(
        OperatorReplayKafkaDeadLetterRecordCommand
            command
    );
}
