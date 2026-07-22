package com.nursena.payflow.eventprocessing.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model
    .KafkaDeadLetterRecordStatus;

public record KafkaDeadLetterRecordSummary(
    UUID id,
    KafkaDeadLetterRecordStatus status,
    String deadLetterTopic,
    int deadLetterPartition,
    long deadLetterOffset,
    String originalTopic,
    int originalPartition,
    long originalOffset,
    String originalConsumerGroup,
    String exceptionType,
    int replayCount,
    int replayAttemptBase,
    Instant receivedAt,
    Instant lastReplayedAt,
    UUID replayOriginId,
    boolean payloadAvailable
) {

    public KafkaDeadLetterRecordSummary {
        id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        status =
            Objects.requireNonNull(
                status,
                "status must not be null"
            );

        deadLetterTopic =
            validateText(
                deadLetterTopic,
                "deadLetterTopic"
            );

        originalTopic =
            validateText(
                originalTopic,
                "originalTopic"
            );

        originalConsumerGroup =
            validateText(
                originalConsumerGroup,
                "originalConsumerGroup"
            );

        exceptionType =
            validateText(
                exceptionType,
                "exceptionType"
            );

        validateNonNegative(
            deadLetterPartition,
            "deadLetterPartition"
        );

        validateNonNegative(
            deadLetterOffset,
            "deadLetterOffset"
        );

        validateNonNegative(
            originalPartition,
            "originalPartition"
        );

        validateNonNegative(
            originalOffset,
            "originalOffset"
        );

        validateNonNegative(
            replayCount,
            "replayCount"
        );

        validateNonNegative(
            replayAttemptBase,
            "replayAttemptBase"
        );

        receivedAt =
            Objects.requireNonNull(
                receivedAt,
                "receivedAt must not be null"
            );

        if (
            lastReplayedAt != null
                && lastReplayedAt.isBefore(receivedAt)
        ) {
            throw new IllegalArgumentException(
                "lastReplayedAt must not be "
                    + "before receivedAt"
            );
        }

        replayOriginId =
            Objects.requireNonNull(
                replayOriginId,
                "replayOriginId must not be null"
            );

        validateTotalReplayAttempts(
            replayAttemptBase,
            replayCount
        );
    }

    public int totalReplayAttempts() {
        return Math.addExact(
            replayAttemptBase,
            replayCount
        );
    }

    private static String validateText(
        String value,
        String fieldName
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not be blank"
            );
        }

        return value;
    }

    private static void validateNonNegative(
        long value,
        String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not be negative"
            );
        }
    }

    private static void
    validateTotalReplayAttempts(
        int replayAttemptBase,
        int replayCount
    ) {
        try {
            Math.addExact(
                replayAttemptBase,
                replayCount
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                "total replay attempt count "
                    + "must not overflow",
                exception
            );
        }
    }
}
