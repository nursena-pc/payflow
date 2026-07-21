package com.nursena.payflow.eventprocessing.configuration;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix =
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-replay"
)
public record KafkaDeadLetterReplayProperties(
    String workerId,
    Duration leaseDuration,
    int maxAttempts
) {

    private static final int
        MAX_WORKER_ID_LENGTH = 200;

    public KafkaDeadLetterReplayProperties {
        if (
            workerId == null
                || workerId.isBlank()
        ) {
            throw new IllegalArgumentException(
                "workerId must not be blank."
            );
        }

        if (
            workerId.length()
                > MAX_WORKER_ID_LENGTH
        ) {
            throw new IllegalArgumentException(
                "workerId must not exceed "
                    + MAX_WORKER_ID_LENGTH
                    + " characters."
            );
        }

        Objects.requireNonNull(
            leaseDuration,
            "leaseDuration must not be null"
        );

        if (
            leaseDuration.isZero()
                || leaseDuration.isNegative()
        ) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive."
            );
        }

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "maxAttempts must be positive."
            );
        }
    }
}
