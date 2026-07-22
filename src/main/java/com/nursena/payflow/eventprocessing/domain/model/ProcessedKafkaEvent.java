package com.nursena.payflow.eventprocessing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProcessedKafkaEvent(
    String consumerName,
    UUID eventId,
    String eventType,
    int eventVersion,
    String topic,
    int partitionNumber,
    long recordOffset,
    Instant processedAt
) {

    private static final int MAX_CONSUMER_NAME_LENGTH =
        200;

    private static final int MAX_EVENT_TYPE_LENGTH =
        200;

    private static final int MAX_TOPIC_LENGTH =
        200;

    public ProcessedKafkaEvent {
        requireText(
            consumerName,
            "consumerName",
            MAX_CONSUMER_NAME_LENGTH
        );

        Objects.requireNonNull(
            eventId,
            "eventId must not be null"
        );

        requireText(
            eventType,
            "eventType",
            MAX_EVENT_TYPE_LENGTH
        );

        if (eventVersion <= 0) {
            throw new IllegalArgumentException(
                "eventVersion must be positive."
            );
        }

        requireText(
            topic,
            "topic",
            MAX_TOPIC_LENGTH
        );

        if (partitionNumber < 0) {
            throw new IllegalArgumentException(
                "partitionNumber must not be negative."
            );
        }

        if (recordOffset < 0) {
            throw new IllegalArgumentException(
                "recordOffset must not be negative."
            );
        }

        Objects.requireNonNull(
            processedAt,
            "processedAt must not be null"
        );
    }

    private static void requireText(
        String value,
        String fieldName,
        int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank."
            );
        }

        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters."
            );
        }
    }
}
