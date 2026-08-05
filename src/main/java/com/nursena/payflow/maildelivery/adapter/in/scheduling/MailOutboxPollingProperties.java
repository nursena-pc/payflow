package com.nursena.payflow.maildelivery.adapter.in.scheduling;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.mail.outbox.polling")
public record MailOutboxPollingProperties(
    boolean enabled,
    String workerId,
    int batchSize,
    Duration leaseDuration,
    Duration fixedDelay,
    Duration initialDelay
) {

    public MailOutboxPollingProperties {
        if (workerId == null || workerId.isBlank() || workerId.length() > 200) {
            throw new IllegalArgumentException("workerId must be non-blank and at most 200 characters");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        leaseDuration = requirePositive(leaseDuration, "leaseDuration");
        fixedDelay = requirePositive(fixedDelay, "fixedDelay");
        Objects.requireNonNull(initialDelay, "initialDelay must not be null");
        if (initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative");
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
