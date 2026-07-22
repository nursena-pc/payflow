package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.DiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterDiscardPort;
import org.springframework.transaction.annotation.Transactional;

public class DiscardKafkaDeadLetterRecordService
    implements DiscardKafkaDeadLetterRecordUseCase {

    private final KafkaDeadLetterDiscardPort
        discardPort;

    public DiscardKafkaDeadLetterRecordService(
        KafkaDeadLetterDiscardPort discardPort
    ) {
        this.discardPort =
            Objects.requireNonNull(
                discardPort,
                "discardPort must not be null"
            );
    }

    @Override
    @Transactional
    public DiscardKafkaDeadLetterRecordResult discard(
        DiscardKafkaDeadLetterRecordCommand command
    ) {
        DiscardKafkaDeadLetterRecordCommand
            validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        DiscardKafkaDeadLetterRecordResult result =
            discardPort.discard(
                validatedCommand.recordId()
            );

        return Objects.requireNonNull(
            result,
            "discard result must not be null"
        );
    }
}
