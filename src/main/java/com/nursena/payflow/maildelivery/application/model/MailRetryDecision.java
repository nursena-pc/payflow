package com.nursena.payflow.maildelivery.application.model;

import java.time.Instant;
import java.util.Objects;

public record MailRetryDecision(
    boolean shouldRetry,
    Instant nextAvailableAt
) {

    public MailRetryDecision {
        if (shouldRetry) {
            Objects.requireNonNull(
                nextAvailableAt,
                "nextAvailableAt must not be null for retry"
            );
        } else if (nextAvailableAt != null) {
            throw new IllegalArgumentException(
                "nextAvailableAt must be null for terminal failure"
            );
        }
    }

    public static MailRetryDecision retryAt(Instant nextAvailableAt) {
        return new MailRetryDecision(true, nextAvailableAt);
    }

    public static MailRetryDecision terminalFailure() {
        return new MailRetryDecision(false, null);
    }
}
