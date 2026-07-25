package com.nursena.payflow.eventprocessing.application.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record KafkaDeadLetterCommandAuditTimeline(
    UUID commandId,
    List<KafkaDeadLetterCommandAudit> entries
) {

    public KafkaDeadLetterCommandAuditTimeline {
        commandId =
            Objects.requireNonNull(
                commandId,
                "commandId must not be null"
            );
        entries =
            List.copyOf(
                Objects.requireNonNull(
                    entries,
                    "entries must not be null"
                )
            );

        validateEntries(
            commandId,
            entries
        );
    }

    public boolean complete() {
        return entries.size() == 2;
    }

    private static void validateEntries(
        UUID commandId,
        List<KafkaDeadLetterCommandAudit> entries
    ) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                "entries must not be empty"
            );
        }

        if (entries.size() > 2) {
            throw new IllegalArgumentException(
                "entries must contain at most two audits"
            );
        }

        KafkaDeadLetterCommandAudit attempted =
            entries.get(0);

        validateCommandId(
            commandId,
            attempted
        );

        if (
            attempted.stage()
                != KafkaDeadLetterCommandAuditStage
                    .ATTEMPTED
        ) {
            throw new IllegalArgumentException(
                "timeline must start with ATTEMPTED"
            );
        }

        if (entries.size() == 1) {
            return;
        }

        KafkaDeadLetterCommandAudit completed =
            entries.get(1);

        validateSharedCommandState(
            attempted,
            completed
        );

        if (
            completed.stage()
                != KafkaDeadLetterCommandAuditStage
                    .COMPLETED
        ) {
            throw new IllegalArgumentException(
                "timeline must end with COMPLETED"
            );
        }

        if (
            completed.occurredAt()
                .isBefore(attempted.occurredAt())
        ) {
            throw new IllegalArgumentException(
                "COMPLETED must not occur before ATTEMPTED"
            );
        }
    }

    private static void validateCommandId(
        UUID commandId,
        KafkaDeadLetterCommandAudit audit
    ) {
        Objects.requireNonNull(
            audit,
            "entries must not contain null"
        );

        if (!commandId.equals(audit.commandId())) {
            throw new IllegalArgumentException(
                "entry commandId must match timeline commandId"
            );
        }
    }

    private static void validateSharedCommandState(
        KafkaDeadLetterCommandAudit attempted,
        KafkaDeadLetterCommandAudit completed
    ) {
        validateCommandId(
            attempted.commandId(),
            completed
        );

        if (
            !attempted.operatorId()
                .equals(completed.operatorId())
        ) {
            throw new IllegalArgumentException(
                "timeline operatorId values must match"
            );
        }

        if (
            !attempted.deadLetterRecordId()
                .equals(completed.deadLetterRecordId())
        ) {
            throw new IllegalArgumentException(
                "timeline deadLetterRecordId values must match"
            );
        }

        if (
            attempted.commandType()
                != completed.commandType()
        ) {
            throw new IllegalArgumentException(
                "timeline commandType values must match"
            );
        }
    }
}
