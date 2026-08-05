package com.nursena.payflow.maildelivery.application.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.maildelivery.application.model.MailRetryDecision;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxStatus;

public final class MailRetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public MailRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maximumDelay
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.initialDelay = requirePositive(initialDelay, "initialDelay");
        this.maximumDelay = requirePositive(maximumDelay, "maximumDelay");
        if (this.maximumDelay.compareTo(this.initialDelay) < 0) {
            throw new IllegalArgumentException("maximumDelay must not be less than initialDelay");
        }
        this.maxAttempts = maxAttempts;
    }

    public MailRetryDecision decide(
        MailOutboxMessage message,
        Instant failedAt
    ) {
        Objects.requireNonNull(message, "message must not be null");
        Instant checkedFailedAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
        if (message.status() != MailOutboxStatus.PROCESSING) {
            throw new IllegalArgumentException("message must be PROCESSING");
        }
        if (message.attemptCount() >= maxAttempts) {
            return MailRetryDecision.terminalFailure();
        }
        Duration delay = initialDelay;
        for (int attempt = 1; attempt < message.attemptCount(); attempt++) {
            if (delay.compareTo(maximumDelay) >= 0) {
                delay = maximumDelay;
                break;
            }
            try {
                Duration doubled = delay.multipliedBy(2);
                delay = doubled.compareTo(maximumDelay) > 0
                    ? maximumDelay
                    : doubled;
            } catch (ArithmeticException exception) {
                delay = maximumDelay;
                break;
            }
        }
        Instant next = checkedFailedAt.plus(delay);
        if (!message.expiresAt().isAfter(next)) {
            return MailRetryDecision.terminalFailure();
        }
        return MailRetryDecision.retryAt(next);
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
