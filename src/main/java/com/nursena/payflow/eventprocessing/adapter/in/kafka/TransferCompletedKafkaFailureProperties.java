package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix =
        "payflow.event-processing"
            + ".transfer-completed.failure"
)
record TransferCompletedKafkaFailureProperties(
    String deadLetterTopic,
    int maxRetries,
    Duration initialDelay,
    double multiplier,
    Duration maximumDelay,
    Duration sendTimeout
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    TransferCompletedKafkaFailureProperties {
        deadLetterTopic =
            validateTopic(deadLetterTopic);

        if (maxRetries < 0) {
            throw new IllegalArgumentException(
                "maxRetries must not be negative."
            );
        }

        initialDelay =
            validatePositiveDuration(
                initialDelay,
                "initialDelay"
            );

        if (multiplier < 1.0) {
            throw new IllegalArgumentException(
                "multiplier must be greater than "
                    + "or equal to 1.0."
            );
        }

        maximumDelay =
            validatePositiveDuration(
                maximumDelay,
                "maximumDelay"
            );

        if (maximumDelay.compareTo(
            initialDelay
        ) < 0) {
            throw new IllegalArgumentException(
                "maximumDelay must be greater than "
                    + "or equal to initialDelay."
            );
        }

        sendTimeout =
            validatePositiveDuration(
                sendTimeout,
                "sendTimeout"
            );
    }

    private static String validateTopic(
        String topic
    ) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException(
                "deadLetterTopic must not be blank."
            );
        }

        if (topic.length() > MAX_TOPIC_LENGTH) {
            throw new IllegalArgumentException(
                "deadLetterTopic must not exceed "
                    + MAX_TOPIC_LENGTH
                    + " characters."
            );
        }

        return topic;
    }

    private static Duration
    validatePositiveDuration(
        Duration value,
        String fieldName
    ) {
        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                fieldName + " must be positive."
            );
        }

        return value;
    }
}
