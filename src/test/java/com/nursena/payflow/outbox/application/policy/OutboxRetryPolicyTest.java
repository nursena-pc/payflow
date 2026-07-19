package com.nursena.payflow.outbox.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.application.model.OutboxRetryDecision;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T12:00:00Z"
        );

    private static final Instant FAILED_AT =
        CREATED_AT.plusSeconds(60);

    private final OutboxRetryPolicy policy =
        new OutboxRetryPolicy(
            5,
            Duration.ofSeconds(10),
            Duration.ofSeconds(60)
        );

    @Test
    void shouldUseInitialDelayAfterFirstAttempt() {
        OutboxRetryDecision decision =
            policy.decide(
                processingEvent(1),
                FAILED_AT
            );

        assertThat(decision.shouldRetry())
            .isTrue();

        assertThat(decision.nextAvailableAt())
            .isEqualTo(
                FAILED_AT.plusSeconds(10)
            );
    }

    @Test
    void shouldApplyExponentialBackoff() {
        OutboxRetryDecision decision =
            policy.decide(
                processingEvent(3),
                FAILED_AT
            );

        assertThat(decision.shouldRetry())
            .isTrue();

        assertThat(decision.nextAvailableAt())
            .isEqualTo(
                FAILED_AT.plusSeconds(40)
            );
    }

    @Test
    void shouldCapDelayAtMaximum() {
        OutboxRetryDecision decision =
            policy.decide(
                processingEvent(4),
                FAILED_AT
            );

        assertThat(decision.shouldRetry())
            .isTrue();

        assertThat(decision.nextAvailableAt())
            .isEqualTo(
                FAILED_AT.plusSeconds(60)
            );
    }

    @Test
    void shouldReturnTerminalFailureAtMaximumAttempts() {
        OutboxRetryDecision decision =
            policy.decide(
                processingEvent(5),
                FAILED_AT
            );

        assertThat(decision.shouldRetry())
            .isFalse();

        assertThat(decision.nextAvailableAt())
            .isNull();
    }

    @Test
    void shouldRejectEventThatIsNotProcessing() {
        OutboxEvent pending =
            OutboxEvent.pending(
                UUID.randomUUID(),
                "PAYMENT_TRANSACTION",
                UUID.randomUUID(),
                "wallet.transfer.completed",
                1,
                "wallet.transfer.completed",
                "partition-1",
                UUID.randomUUID().toString(),
                """
                {
                  "eventType": "wallet.transfer.completed"
                }
                """,
                CREATED_AT
            );

        assertThatThrownBy(() ->
            policy.decide(
                pending,
                FAILED_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "event must be PROCESSING."
            );
    }

    private static OutboxEvent processingEvent(
        int attemptCount
    ) {
        UUID eventId = UUID.randomUUID();

        return OutboxEvent.rehydrate(
            eventId,
            "PAYMENT_TRANSACTION",
            UUID.randomUUID(),
            "wallet.transfer.completed",
            1,
            "wallet.transfer.completed",
            "partition-1",
            "wallet.transfer.completed:1:"
                + eventId,
            """
            {
              "eventType": "wallet.transfer.completed"
            }
            """,
            OutboxStatus.PROCESSING,
            attemptCount,
            CREATED_AT,
            CREATED_AT.plusSeconds(30),
            CREATED_AT.plusSeconds(90),
            "publisher-1",
            CREATED_AT,
            null,
            null
        );
    }
}
