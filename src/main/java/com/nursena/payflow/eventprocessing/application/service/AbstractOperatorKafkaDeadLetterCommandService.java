package com.nursena.payflow.eventprocessing.application.service;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import com.nursena.payflow.eventprocessing.domain.exception.KafkaDeadLetterCommandAuditException;

abstract class
AbstractOperatorKafkaDeadLetterCommandService {

    private final KafkaDeadLetterCommandAuditPort
        auditPort;

    private final Clock clock;

    private final Supplier<UUID> idSupplier;

    AbstractOperatorKafkaDeadLetterCommandService(
        KafkaDeadLetterCommandAuditPort auditPort,
        Clock clock,
        Supplier<UUID> idSupplier
    ) {
        this.auditPort =
            Objects.requireNonNull(
                auditPort,
                "auditPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.idSupplier =
            Objects.requireNonNull(
                idSupplier,
                "idSupplier must not be null"
            );
    }

    final <R> R executeAudited(
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        Supplier<R> commandAction,
        Function<R, KafkaDeadLetterCommandAuditOutcome>
            outcomeMapper
    ) {
        UUID validatedOperatorId =
            Objects.requireNonNull(
                operatorId,
                "operatorId must not be null"
            );

        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        KafkaDeadLetterCommandType
            validatedCommandType =
            Objects.requireNonNull(
                commandType,
                "commandType must not be null"
            );

        Supplier<R> validatedCommandAction =
            Objects.requireNonNull(
                commandAction,
                "commandAction must not be null"
            );

        Function<
            R,
            KafkaDeadLetterCommandAuditOutcome
            > validatedOutcomeMapper =
            Objects.requireNonNull(
                outcomeMapper,
                "outcomeMapper must not be null"
            );

        UUID commandId = appendAttempted(
            validatedOperatorId,
            validatedRecordId,
            validatedCommandType
        );

        R result;

        try {
            result =
                Objects.requireNonNull(
                    validatedCommandAction.get(),
                    "command result must not be null"
                );
        } catch (RuntimeException exception) {
            throw internalFailure(
                commandId,
                validatedOperatorId,
                validatedRecordId,
                validatedCommandType,
                exception
            );
        }

        KafkaDeadLetterCommandAuditOutcome outcome;

        try {
            outcome =
                Objects.requireNonNull(
                    validatedOutcomeMapper.apply(result),
                    "audit outcome must not be null"
                );
        } catch (RuntimeException exception) {
            throw internalFailure(
                commandId,
                validatedOperatorId,
                validatedRecordId,
                validatedCommandType,
                exception
            );
        }

        appendCompleted(
            commandId,
            validatedOperatorId,
            validatedRecordId,
            validatedCommandType,
            outcome
        );

        return result;
    }

    private UUID appendAttempted(
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType
    ) {
        try {
            UUID commandId = nextId();

            auditPort.append(
                KafkaDeadLetterCommandAudit.attempted(
                    nextId(),
                    commandId,
                    operatorId,
                    recordId,
                    commandType,
                    clock.instant()
                )
            );

            return commandId;
        } catch (RuntimeException exception) {
            throw KafkaDeadLetterCommandAuditException
                .attemptPersistenceFailed(exception);
        }
    }

    private void appendCompleted(
        UUID commandId,
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome
    ) {
        try {
            auditPort.append(
                KafkaDeadLetterCommandAudit.completed(
                    nextId(),
                    commandId,
                    operatorId,
                    recordId,
                    commandType,
                    outcome,
                    clock.instant()
                )
            );
        } catch (RuntimeException exception) {
            throw KafkaDeadLetterCommandAuditException
                .completionPersistenceFailed(exception);
        }
    }

    private KafkaDeadLetterCommandAuditException
    internalFailure(
        UUID commandId,
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        RuntimeException commandFailure
    ) {
        try {
            auditPort.append(
                KafkaDeadLetterCommandAudit.completed(
                    nextId(),
                    commandId,
                    operatorId,
                    recordId,
                    commandType,
                    KafkaDeadLetterCommandAuditOutcome
                        .INTERNAL_FAILURE,
                    clock.instant()
                )
            );
        } catch (RuntimeException auditFailure) {
            KafkaDeadLetterCommandAuditException
                failure =
                KafkaDeadLetterCommandAuditException
                    .completionPersistenceFailed(
                        auditFailure
                    );

            failure.addSuppressed(commandFailure);
            return failure;
        }

        return KafkaDeadLetterCommandAuditException
            .commandInternalFailure(commandFailure);
    }

    private UUID nextId() {
        return Objects.requireNonNull(
            idSupplier.get(),
            "idSupplier must not return null"
        );
    }
}
