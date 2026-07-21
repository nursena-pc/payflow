package com.nursena.payflow.eventprocessing.application.port.in;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;

public interface ClaimKafkaDeadLetterRecordUseCase {

    ClaimKafkaDeadLetterRecordResult claim(
        ClaimKafkaDeadLetterRecordCommand command
    );
}
