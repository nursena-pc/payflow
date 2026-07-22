package com.nursena.payflow.eventprocessing.domain.exception;

import java.util.Objects;
import java.util.UUID;

public final class
KafkaDeadLetterRecordNotFoundException
    extends RuntimeException {

    public static final String CODE =
        "KAFKA_DEAD_LETTER_RECORD_NOT_FOUND";

    private final UUID recordId;

    public KafkaDeadLetterRecordNotFoundException(
        UUID recordId
    ) {
        super(
            "Kafka dead-letter record "
                + "was not found."
        );

        this.recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );
    }

    public String getCode() {
        return CODE;
    }

    public UUID getRecordId() {
        return recordId;
    }
}
