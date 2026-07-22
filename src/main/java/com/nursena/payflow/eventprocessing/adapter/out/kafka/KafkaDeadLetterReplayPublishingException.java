package com.nursena.payflow.eventprocessing.adapter.out.kafka;

import java.util.Objects;
import java.util.UUID;

public final class
KafkaDeadLetterReplayPublishingException
    extends RuntimeException {

    private final UUID recordId;
    private final String topic;

    public KafkaDeadLetterReplayPublishingException(
        UUID recordId,
        String topic,
        String reason,
        Throwable cause
    ) {
        super(
            message(
                recordId,
                topic,
                reason
            ),
            cause
        );

        this.recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        this.topic =
            requireText(
                topic,
                "topic"
            );
    }

    public UUID recordId() {
        return recordId;
    }

    public String topic() {
        return topic;
    }

    private static String message(
        UUID recordId,
        String topic,
        String reason
    ) {
        Objects.requireNonNull(
            recordId,
            "recordId must not be null"
        );

        return "Kafka dead-letter replay "
            + "publication failed. "
            + "recordId="
            + recordId
            + ", topic="
            + requireText(
            topic,
            "topic"
        )
            + ", reason="
            + requireText(
            reason,
            "reason"
        );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not be blank."
            );
        }

        return value;
    }
}
