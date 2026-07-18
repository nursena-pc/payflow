package com.nursena.payflow.outbox.application.policy;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.outbox.application.model.OutboxRetryDecision;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;

public final class OutboxRetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public OutboxRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maximumDelay
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "maxAttempts must be positive."
            );
        }

        this.initialDelay =
            requirePositiveDuration(
                initialDelay,
                "initialDelay"
            );

        this.maximumDelay =
            requirePositiveDuration(
                maximumDelay,
                "maximumDelay"
            );

        if (
            this.maximumDelay.compareTo(
                this.initialDelay
            ) < 0
        ) {
            throw new IllegalArgumentException(
                "maximumDelay must not be "
                    + "less than initialDelay."
            );
        }

        this.maxAttempts = maxAttempts;
    }

    public OutboxRetryDecision decide(
        OutboxEvent event,
        Instant failedAt
    ) {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        Instant validatedFailedAt =
            Objects.requireNonNull(
                failedAt,
                "failedAt must not be null"
            );

        if (
            event.status()
                != OutboxStatus.PROCESSING
        ) {
            throw new IllegalArgumentException(
                "event must be PROCESSING."
            );
        }

        if (event.attemptCount() <= 0) {
            throw new IllegalArgumentException(
                "event attemptCount must be positive."
            );
        }

        if (
            event.attemptCount()
                >= maxAttempts
        ) {
            return OutboxRetryDecision
                .terminalFailure();
        }

        Duration delay =
            calculateDelay(
                event.attemptCount()
            );

        try {
            return OutboxRetryDecision.retryAt(
                validatedFailedAt.plus(delay)
            );
        } catch (
            DateTimeException
            | ArithmeticException exception
        ) {
            throw new IllegalArgumentException(
                "Retry delay produces an invalid "
                    + "nextAvailableAt.",
                exception
            );
        }
    }

    private Duration calculateDelay(
        int attemptCount
    ) {
        Duration delay = initialDelay;

        for (
            int attempt = 1;
            attempt < attemptCount;
            attempt++
        ) {
            if (
                delay.compareTo(
                    maximumDelay
                ) >= 0
            ) {
                return maximumDelay;
            }

            try {
                Duration doubled =
                    delay.multipliedBy(2);

                delay =
                    doubled.compareTo(
                        maximumDelay
                    ) > 0
                        ? maximumDelay
                        : doubled;
            } catch (ArithmeticException exception) {
                return maximumDelay;
            }
        }

        return delay;
    }

    private static Duration
    requirePositiveDuration(
        Duration value,
        String fieldName
    ) {
        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (
            value.isZero()
                || value.isNegative()
        ) {
            throw new IllegalArgumentException(
                fieldName
                    + " must be positive."
            );
        }

        return value;
    }
}
