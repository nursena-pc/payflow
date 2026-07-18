package com.nursena.payflow.outbox.adapter.out.kafka;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.outbox.kafka"
)
public record OutboxKafkaPublisherProperties(
    Duration sendTimeout
) {

    public OutboxKafkaPublisherProperties {
        Objects.requireNonNull(
            sendTimeout,
            "sendTimeout must not be null"
        );

        if (
            sendTimeout.isZero()
                || sendTimeout.isNegative()
        ) {
            throw new IllegalArgumentException(
                "sendTimeout must be positive."
            );
        }

        if (sendTimeout.toMillis() == 0) {
            throw new IllegalArgumentException(
                "sendTimeout must be at least "
                    + "one millisecond."
            );
        }
    }
}
