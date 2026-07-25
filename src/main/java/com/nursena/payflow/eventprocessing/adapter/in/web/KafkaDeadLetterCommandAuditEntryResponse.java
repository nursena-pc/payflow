package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterCommandAuditEntryResponse",
    description =
        "Safe append-only audit metadata for one "
            + "Kafka dead-letter operator command stage."
)
public record KafkaDeadLetterCommandAuditEntryResponse(
    @Schema(format = "uuid")
    UUID id,

    @Schema(format = "uuid")
    UUID commandId,

    KafkaDeadLetterCommandAuditStage stage,

    @Schema(format = "uuid")
    UUID operatorId,

    @Schema(format = "uuid")
    UUID deadLetterRecordId,

    KafkaDeadLetterCommandType commandType,

    KafkaDeadLetterCommandAuditOutcome outcome,

    @Schema(
        description =
            "Allowlisted error code for a completed "
                + "command outcome."
    )
    String errorCode,

    @Schema(format = "date-time")
    Instant occurredAt
) {
    static KafkaDeadLetterCommandAuditEntryResponse from(
        KafkaDeadLetterCommandAudit audit
    ) {
        KafkaDeadLetterCommandAudit validatedAudit =
            Objects.requireNonNull(
                audit,
                "audit must not be null"
            );

        return new KafkaDeadLetterCommandAuditEntryResponse(
            validatedAudit.id(),
            validatedAudit.commandId(),
            validatedAudit.stage(),
            validatedAudit.operatorId(),
            validatedAudit.deadLetterRecordId(),
            validatedAudit.commandType(),
            validatedAudit.outcome(),
            validatedAudit.errorCode(),
            validatedAudit.occurredAt()
        );
    }
}
