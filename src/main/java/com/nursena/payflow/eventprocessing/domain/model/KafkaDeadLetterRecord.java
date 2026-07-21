package com.nursena.payflow.eventprocessing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KafkaDeadLetterRecord(
    UUID id,
    String deadLetterTopic,
    int deadLetterPartition,
    long deadLetterOffset,
    String originalTopic,
    int originalPartition,
    long originalOffset,
    String originalConsumerGroup,
    String recordKey,
    String payload,
    String exceptionType,
    String exceptionMessage,
    KafkaDeadLetterRecordStatus status,
    int replayCount,
    Instant receivedAt,
    Instant lastReplayedAt,
    String replayLeaseOwner,
    Instant replayLeaseUntil,
    String lastReplayError
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    private static final int
        MAX_CONSUMER_GROUP_LENGTH = 255;

    private static final int
        MAX_EXCEPTION_TYPE_LENGTH = 500;

    private static final int
        MAX_REPLAY_LEASE_OWNER_LENGTH = 200;

    public KafkaDeadLetterRecord {
        id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        deadLetterTopic =
            validateText(
                deadLetterTopic,
                "deadLetterTopic",
                MAX_TOPIC_LENGTH
            );

        validateNonNegative(
            deadLetterPartition,
            "deadLetterPartition"
        );

        validateNonNegative(
            deadLetterOffset,
            "deadLetterOffset"
        );

        originalTopic =
            validateText(
                originalTopic,
                "originalTopic",
                MAX_TOPIC_LENGTH
            );

        validateNonNegative(
            originalPartition,
            "originalPartition"
        );

        validateNonNegative(
            originalOffset,
            "originalOffset"
        );

        originalConsumerGroup =
            validateText(
                originalConsumerGroup,
                "originalConsumerGroup",
                MAX_CONSUMER_GROUP_LENGTH
            );

        exceptionType =
            validateText(
                exceptionType,
                "exceptionType",
                MAX_EXCEPTION_TYPE_LENGTH
            );

        status =
            Objects.requireNonNull(
                status,
                "status must not be null"
            );

        receivedAt =
            Objects.requireNonNull(
                receivedAt,
                "receivedAt must not be null"
            );

        validateReplayState(
            status,
            replayCount,
            lastReplayedAt,
            replayLeaseOwner,
            replayLeaseUntil
        );
    }

    private static void validateReplayState(
        KafkaDeadLetterRecordStatus status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil
    ) {
        validateNonNegative(
            replayCount,
            "replayCount"
        );

        if (
            replayCount == 0
                && lastReplayedAt != null
        ) {
            throw new IllegalArgumentException(
                "lastReplayedAt must be null "
                    + "when replayCount is zero."
            );
        }

        if (
            replayCount > 0
                && lastReplayedAt == null
        ) {
            throw new IllegalArgumentException(
                "lastReplayedAt must not be null "
                    + "when replayCount is positive."
            );
        }

        if (
            status
                == KafkaDeadLetterRecordStatus
                .RECEIVED
                && replayCount != 0
        ) {
            throw new IllegalArgumentException(
                "RECEIVED records must have "
                    + "a zero replayCount."
            );
        }

        boolean requiresReplayAttempt =
            status
                == KafkaDeadLetterRecordStatus
                .REPLAYING
                || status
                == KafkaDeadLetterRecordStatus
                .REPLAYED
                || status
                == KafkaDeadLetterRecordStatus
                .REPLAY_FAILED;

        if (
            requiresReplayAttempt
                && replayCount == 0
        ) {
            throw new IllegalArgumentException(
                status
                    + " records must have "
                    + "a positive replayCount."
            );
        }

        validateReplayLease(
            status,
            replayLeaseOwner,
            replayLeaseUntil
        );
    }

    private static void validateReplayLease(
        KafkaDeadLetterRecordStatus status,
        String replayLeaseOwner,
        Instant replayLeaseUntil
    ) {
        if (
            status
                == KafkaDeadLetterRecordStatus
                .REPLAYING
        ) {
            validateText(
                replayLeaseOwner,
                "replayLeaseOwner",
                MAX_REPLAY_LEASE_OWNER_LENGTH
            );

            Objects.requireNonNull(
                replayLeaseUntil,
                "replayLeaseUntil must not be null "
                    + "for REPLAYING records"
            );

            return;
        }

        if (
            replayLeaseOwner != null
                || replayLeaseUntil != null
        ) {
            throw new IllegalArgumentException(
                "Replay lease fields must be null "
                    + "unless status is REPLAYING."
            );
        }
    }

    private static String validateText(
        String value,
        String fieldName,
        int maximumLength
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

        if (
            value.length()
                > maximumLength
        ) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters."
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
                    + " must not be negative."
            );
        }
    }
}
