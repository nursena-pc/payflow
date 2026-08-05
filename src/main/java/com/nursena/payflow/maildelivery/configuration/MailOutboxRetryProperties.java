package com.nursena.payflow.maildelivery.configuration;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.mail.outbox.retry")
public record MailOutboxRetryProperties(
    int maxAttempts,
    Duration initialDelay,
    Duration maximumDelay
) {

    public MailOutboxRetryProperties {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        initialDelay = requirePositive(initialDelay, "initialDelay");
        maximumDelay = requirePositive(maximumDelay, "maximumDelay");
        if (maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maximumDelay must not be less than initialDelay");
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
