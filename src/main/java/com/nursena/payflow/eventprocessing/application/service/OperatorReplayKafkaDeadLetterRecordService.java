package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.model.OperatorReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.OperatorReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;

public class OperatorReplayKafkaDeadLetterRecordService
    extends AbstractOperatorKafkaDeadLetterCommandService
    implements OperatorReplayKafkaDeadLetterRecordUseCase {

    private final ReplayKafkaDeadLetterRecordUseCase
        replayUseCase;

    public OperatorReplayKafkaDeadLetterRecordService(
        ReplayKafkaDeadLetterRecordUseCase replayUseCase,
        KafkaDeadLetterCommandAuditPort auditPort,
        Clock clock,
        Supplier<UUID> idSupplier
    ) {
        super(
            auditPort,
            clock,
            idSupplier
        );

        this.replayUseCase =
            Objects.requireNonNull(
                replayUseCase,
                "replayUseCase must not be null"
            );
    }

    @Override
    public ReplayKafkaDeadLetterRecordResult replay(
        OperatorReplayKafkaDeadLetterRecordCommand
            command
    ) {
        OperatorReplayKafkaDeadLetterRecordCommand
            validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        return executeAudited(
            validatedCommand.operatorId(),
            validatedCommand.recordId(),
            KafkaDeadLetterCommandType.REPLAY,
            () -> replayUseCase.replay(
                new ReplayKafkaDeadLetterRecordCommand(
                    validatedCommand.recordId()
                )
            ),
            KafkaDeadLetterCommandAuditOutcomeMapper
                ::fromReplay
        );
    }
}
