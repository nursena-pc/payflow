package com.nursena.payflow.eventprocessing.application.model;

public record RecordKafkaDeadLetterCommand(
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
    String exceptionMessage
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    private static final int
        MAX_CONSUMER_GROUP_LENGTH = 255;

    private static final int
        MAX_EXCEPTION_TYPE_LENGTH = 500;

    public RecordKafkaDeadLetterCommand {
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
    }

    private static String validateText(
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
