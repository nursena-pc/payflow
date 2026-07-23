package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.model.OperatorDiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.port.in.DiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.OperatorDiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;

public class OperatorDiscardKafkaDeadLetterRecordService
    extends AbstractOperatorKafkaDeadLetterCommandService
    implements OperatorDiscardKafkaDeadLetterRecordUseCase {

    private final DiscardKafkaDeadLetterRecordUseCase
        discardUseCase;

    public OperatorDiscardKafkaDeadLetterRecordService(
        DiscardKafkaDeadLetterRecordUseCase discardUseCase,
        KafkaDeadLetterCommandAuditPort auditPort,
        Clock clock,
        Supplier<UUID> idSupplier
    ) {
        super(
            auditPort,
            clock,
            idSupplier
        );

        this.discardUseCase =
            Objects.requireNonNull(
                discardUseCase,
                "discardUseCase must not be null"
            );
    }

    @Override
    public DiscardKafkaDeadLetterRecordResult discard(
        OperatorDiscardKafkaDeadLetterRecordCommand
            command
    ) {
        OperatorDiscardKafkaDeadLetterRecordCommand
            validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        return executeAudited(
            validatedCommand.operatorId(),
            validatedCommand.recordId(),
            KafkaDeadLetterCommandType.DISCARD,
            () -> discardUseCase.discard(
                new DiscardKafkaDeadLetterRecordCommand(
                    validatedCommand.recordId()
                )
            ),
            KafkaDeadLetterCommandAuditOutcomeMapper
                ::fromDiscard
        );
    }
}
