package com.nursena.payflow.eventprocessing.application.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public record KafkaDeadLetterCommandAudit(
    UUID id,
    UUID commandId,
    KafkaDeadLetterCommandAuditStage stage,
    UUID operatorId,
    UUID deadLetterRecordId,
    KafkaDeadLetterCommandType commandType,
    KafkaDeadLetterCommandAuditOutcome outcome,
    String errorCode,
    Instant occurredAt
) {

    public KafkaDeadLetterCommandAudit {
        id = requireIdentifier(id, "id");
        commandId =
            requireIdentifier(
                commandId,
                "commandId"
            );
        stage =
            Objects.requireNonNull(
                stage,
                "stage must not be null"
            );
        operatorId =
            requireIdentifier(
                operatorId,
                "operatorId"
            );
        deadLetterRecordId =
            requireIdentifier(
                deadLetterRecordId,
                "deadLetterRecordId"
            );
        commandType =
            Objects.requireNonNull(
                commandType,
                "commandType must not be null"
            );
        occurredAt =
            Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
            ).truncatedTo(ChronoUnit.MICROS);

        validateStageState(
            stage,
            commandType,
            outcome,
            errorCode
        );
    }

    public static KafkaDeadLetterCommandAudit
    attempted(
        UUID id,
        UUID commandId,
        UUID operatorId,
        UUID deadLetterRecordId,
        KafkaDeadLetterCommandType commandType,
        Instant occurredAt
    ) {
        return new KafkaDeadLetterCommandAudit(
            id,
            commandId,
            KafkaDeadLetterCommandAuditStage
                .ATTEMPTED,
            operatorId,
            deadLetterRecordId,
            commandType,
            null,
            null,
            occurredAt
        );
    }

    public static KafkaDeadLetterCommandAudit
    completed(
        UUID id,
        UUID commandId,
        UUID operatorId,
        UUID deadLetterRecordId,
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome,
        Instant occurredAt
    ) {
        KafkaDeadLetterCommandAuditOutcome
            validatedOutcome =
            Objects.requireNonNull(
                outcome,
                "outcome must not be null"
            );

        return new KafkaDeadLetterCommandAudit(
            id,
            commandId,
            KafkaDeadLetterCommandAuditStage
                .COMPLETED,
            operatorId,
            deadLetterRecordId,
            commandType,
            validatedOutcome,
            validatedOutcome.safeErrorCode(),
            occurredAt
        );
    }

    private static UUID requireIdentifier(
        UUID value,
        String fieldName
    ) {
        return Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );
    }

    private static void validateStageState(
        KafkaDeadLetterCommandAuditStage stage,
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome,
        String errorCode
    ) {
        if (
            stage
                == KafkaDeadLetterCommandAuditStage
                .ATTEMPTED
        ) {
            validateAttemptedState(
                outcome,
                errorCode
            );
            return;
        }

        validateCompletedState(
            commandType,
            outcome,
            errorCode
        );
    }

    private static void validateAttemptedState(
        KafkaDeadLetterCommandAuditOutcome outcome,
        String errorCode
    ) {
        if (outcome != null || errorCode != null) {
            throw new IllegalArgumentException(
                "ATTEMPTED audit must not have "
                    + "an outcome or errorCode."
            );
        }
    }

    private static void validateCompletedState(
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome,
        String errorCode
    ) {
        if (outcome == null) {
            throw new IllegalArgumentException(
                "COMPLETED audit must have an outcome."
            );
        }

        if (!outcome.supports(commandType)) {
            throw new IllegalArgumentException(
                "outcome must be compatible "
                    + "with commandType."
            );
        }

        if (
            !Objects.equals(
                outcome.safeErrorCode(),
                errorCode
            )
        ) {
            throw new IllegalArgumentException(
                "errorCode must match the "
                    + "selected outcome."
            );
        }
    }
}
