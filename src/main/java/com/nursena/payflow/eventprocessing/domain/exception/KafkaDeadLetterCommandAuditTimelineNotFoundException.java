package com.nursena.payflow.eventprocessing.domain.exception;

import java.util.Objects;
import java.util.UUID;

public final class
KafkaDeadLetterCommandAuditTimelineNotFoundException
    extends RuntimeException {

    public static final String CODE =
        "KAFKA_DEAD_LETTER_COMMAND_AUDIT_TIMELINE_NOT_FOUND";

    private final UUID commandId;

    public KafkaDeadLetterCommandAuditTimelineNotFoundException(
        UUID commandId
    ) {
        super(
            "Kafka dead-letter command audit timeline "
                + "was not found."
        );
        this.commandId =
            Objects.requireNonNull(
                commandId,
                "commandId must not be null"
            );
    }

    public String getCode() {
        return CODE;
    }

    public UUID getCommandId() {
        return commandId;
    }
}
