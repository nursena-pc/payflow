package com.nursena.payflow.outbox.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.exception.InvalidOutboxEventException;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import com.nursena.payflow.outbox.domain.exception.InvalidOutboxEventStateException;

class OutboxEventTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-17T19:00:00Z"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final String PUBLISHER_ID =
        "publisher-1";

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    private static final String DEDUPLICATION_KEY =
        EVENT_TYPE + ":1:" + TRANSACTION_ID;

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000001",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1
        }
        """;

    @Test
    void shouldCreatePendingEvent() {
        OutboxEvent event = pendingEvent();

        assertThat(event.id())
            .isEqualTo(EVENT_ID);

        assertThat(event.aggregateType())
            .isEqualTo("PAYMENT_TRANSACTION");

        assertThat(event.aggregateId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(event.eventType())
            .isEqualTo(EVENT_TYPE);

        assertThat(event.eventVersion())
            .isEqualTo(1);

        assertThat(event.topic())
            .isEqualTo(EVENT_TYPE);

        assertThat(event.partitionKey())
            .isEqualTo(TRANSACTION_ID.toString());

        assertThat(event.deduplicationKey())
            .isEqualTo(DEDUPLICATION_KEY);

        assertThat(event.payload())
            .isEqualTo(PAYLOAD);

        assertThat(event.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(event.attemptCount())
            .isZero();

        assertThat(event.availableAt())
            .isEqualTo(CREATED_AT);

        assertThat(event.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(event.lockedAt())
            .isNull();

        assertThat(event.lockedUntil())
            .isNull();

        assertThat(event.lockedBy())
            .isNull();

        assertThat(event.publishedAt())
            .isNull();

        assertThat(event.lastError())
            .isNull();
    }

    @Test
    void shouldRehydrateValidProcessingEvent() {
        Instant lockedAt =
            CREATED_AT.plusSeconds(10);

        Instant lockedUntil =
            lockedAt.plusSeconds(30);

        OutboxEvent event = OutboxEvent.rehydrate(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            DEDUPLICATION_KEY,
            PAYLOAD,
            OutboxStatus.PROCESSING,
            1,
            CREATED_AT,
            lockedAt,
            lockedUntil,
            "publisher-1",
            CREATED_AT,
            null,
            null
        );

        assertThat(event.status())
            .isEqualTo(OutboxStatus.PROCESSING);

        assertThat(event.attemptCount())
            .isEqualTo(1);

        assertThat(event.lockedAt())
            .isEqualTo(lockedAt);

        assertThat(event.lockedUntil())
            .isEqualTo(lockedUntil);

        assertThat(event.lockedBy())
            .isEqualTo("publisher-1");
    }

    @Test
    void shouldRehydrateValidPublishedEvent() {
        Instant publishedAt =
            CREATED_AT.plusSeconds(15);

        OutboxEvent event = OutboxEvent.rehydrate(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            DEDUPLICATION_KEY,
            PAYLOAD,
            OutboxStatus.PUBLISHED,
            1,
            CREATED_AT,
            null,
            null,
            null,
            CREATED_AT,
            publishedAt,
            null
        );

        assertThat(event.status())
            .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(event.publishedAt())
            .isEqualTo(publishedAt);
    }

    @Test
    void shouldRejectBlankEventType() {
        assertThatThrownBy(() ->
            OutboxEvent.pending(
                EVENT_ID,
                "PAYMENT_TRANSACTION",
                TRANSACTION_ID,
                " ",
                1,
                EVENT_TYPE,
                TRANSACTION_ID.toString(),
                DEDUPLICATION_KEY,
                PAYLOAD,
                CREATED_AT
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "eventType must not be blank."
            );
    }

    @Test
    void shouldRejectNonPositiveEventVersion() {
        assertThatThrownBy(() ->
            OutboxEvent.pending(
                EVENT_ID,
                "PAYMENT_TRANSACTION",
                TRANSACTION_ID,
                EVENT_TYPE,
                0,
                EVENT_TYPE,
                TRANSACTION_ID.toString(),
                DEDUPLICATION_KEY,
                PAYLOAD,
                CREATED_AT
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "eventVersion must be positive."
            );
    }

    @Test
    void shouldRejectEmptyPayload() {
        assertThatThrownBy(() ->
            OutboxEvent.pending(
                EVENT_ID,
                "PAYMENT_TRANSACTION",
                TRANSACTION_ID,
                EVENT_TYPE,
                1,
                EVENT_TYPE,
                TRANSACTION_ID.toString(),
                DEDUPLICATION_KEY,
                "{}",
                CREATED_AT
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "payload must contain a non-empty JSON object."
            );
    }

    @Test
    void shouldRejectNegativeAttemptCount() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PENDING,
                -1,
                CREATED_AT,
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "attemptCount must not be negative."
            );
    }

    @Test
    void shouldRejectAvailabilityBeforeCreation() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PENDING,
                0,
                CREATED_AT.minusSeconds(1),
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "availableAt must not be before createdAt."
            );
    }

    @Test
    void shouldRejectProcessingWithoutCompleteLock() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PROCESSING,
                1,
                CREATED_AT,
                CREATED_AT.plusSeconds(1),
                null,
                "publisher-1",
                null
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "PROCESSING event must have complete "
                    + "lock metadata."
            );
    }

    @Test
    void shouldRejectLockMetadataForPendingEvent() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PENDING,
                0,
                CREATED_AT,
                CREATED_AT.plusSeconds(1),
                CREATED_AT.plusSeconds(31),
                "publisher-1",
                null
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "Only PROCESSING events may have "
                    + "lock metadata."
            );
    }

    @Test
    void shouldRejectPublishedEventWithoutTimestamp() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PUBLISHED,
                1,
                CREATED_AT,
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "PUBLISHED event must have publishedAt."
            );
    }

    @Test
    void shouldRejectPublishedAtForPendingEvent() {
        assertThatThrownBy(() ->
            rehydrate(
                OutboxStatus.PENDING,
                0,
                CREATED_AT,
                null,
                null,
                null,
                CREATED_AT.plusSeconds(1)
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "Only PUBLISHED events may have publishedAt."
            );
    }

    @Test
    void shouldClaimAvailablePendingEvent() {
        OutboxEvent pending = pendingEvent();

        OutboxEvent claimed =
            pending.claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        assertThat(pending.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(pending.attemptCount())
            .isZero();

        assertThat(claimed.status())
            .isEqualTo(OutboxStatus.PROCESSING);

        assertThat(claimed.attemptCount())
            .isEqualTo(1);

        assertThat(claimed.lockedAt())
            .isEqualTo(CREATED_AT);

        assertThat(claimed.lockedUntil())
            .isEqualTo(
                CREATED_AT.plusSeconds(30)
            );

        assertThat(claimed.lockedBy())
            .isEqualTo(PUBLISHER_ID);
    }

    @Test
    void shouldRejectClaimBeforeEventIsAvailable() {
        OutboxEvent delayed =
            rehydrate(
                OutboxStatus.PENDING,
                0,
                CREATED_AT.plusSeconds(60),
                null,
                null,
                null,
                null
            );

        assertThatThrownBy(() ->
            delayed.claim(
                PUBLISHER_ID,
                CREATED_AT.plusSeconds(30),
                LEASE_DURATION
            )
        )
            .isInstanceOf(
                InvalidOutboxEventStateException.class
            )
            .hasMessage(
                "PENDING event is not available yet."
            );
    }

    @Test
    void shouldRejectClaimWhileProcessingLeaseIsActive() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        assertThatThrownBy(() ->
            processing.claim(
                "publisher-2",
                CREATED_AT.plusSeconds(10),
                LEASE_DURATION
            )
        )
            .isInstanceOf(
                InvalidOutboxEventStateException.class
            )
            .hasMessage(
                "PROCESSING event lease is still active."
            );
    }

    @Test
    void shouldReclaimExpiredProcessingEvent() {
        OutboxEvent firstClaim =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        Instant reclaimedAt =
            CREATED_AT.plusSeconds(31);

        OutboxEvent reclaimed =
            firstClaim.claim(
                "publisher-2",
                reclaimedAt,
                LEASE_DURATION
            );

        assertThat(reclaimed.status())
            .isEqualTo(OutboxStatus.PROCESSING);

        assertThat(reclaimed.attemptCount())
            .isEqualTo(2);

        assertThat(reclaimed.lockedBy())
            .isEqualTo("publisher-2");

        assertThat(reclaimed.lockedAt())
            .isEqualTo(reclaimedAt);

        assertThat(reclaimed.lockedUntil())
            .isEqualTo(
                reclaimedAt.plusSeconds(30)
            );
    }

    @Test
    void shouldMarkOwnedProcessingEventAsPublished() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        Instant publishedAt =
            CREATED_AT.plusSeconds(10);

        OutboxEvent published =
            processing.markPublished(
                PUBLISHER_ID,
                publishedAt
            );

        assertThat(published.status())
            .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(published.attemptCount())
            .isEqualTo(1);

        assertThat(published.publishedAt())
            .isEqualTo(publishedAt);

        assertThat(published.lockedAt())
            .isNull();

        assertThat(published.lockedUntil())
            .isNull();

        assertThat(published.lockedBy())
            .isNull();

        assertThat(published.lastError())
            .isNull();
    }

    @Test
    void shouldScheduleRetryAfterTemporaryFailure() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        Instant failedAt =
            CREATED_AT.plusSeconds(10);

        Instant nextAvailableAt =
            CREATED_AT.plusSeconds(60);

        OutboxEvent retry =
            processing.scheduleRetry(
                PUBLISHER_ID,
                failedAt,
                nextAvailableAt,
                "Kafka broker is unavailable."
            );

        assertThat(retry.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(retry.attemptCount())
            .isEqualTo(1);

        assertThat(retry.availableAt())
            .isEqualTo(nextAvailableAt);

        assertThat(retry.lockedAt())
            .isNull();

        assertThat(retry.lockedUntil())
            .isNull();

        assertThat(retry.lockedBy())
            .isNull();

        assertThat(retry.lastError())
            .isEqualTo(
                "Kafka broker is unavailable."
            );
    }

    @Test
    void shouldMarkProcessingEventAsFailed() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        OutboxEvent failed =
            processing.markFailed(
                PUBLISHER_ID,
                CREATED_AT.plusSeconds(10),
                "Maximum attempts exceeded."
            );

        assertThat(failed.status())
            .isEqualTo(OutboxStatus.FAILED);

        assertThat(failed.attemptCount())
            .isEqualTo(1);

        assertThat(failed.lockedAt())
            .isNull();

        assertThat(failed.lockedUntil())
            .isNull();

        assertThat(failed.lockedBy())
            .isNull();

        assertThat(failed.lastError())
            .isEqualTo(
                "Maximum attempts exceeded."
            );
    }

    @Test
    void shouldRejectStateChangeByAnotherPublisher() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        assertThatThrownBy(() ->
            processing.markPublished(
                "publisher-2",
                CREATED_AT.plusSeconds(10)
            )
        )
            .isInstanceOf(
                InvalidOutboxEventStateException.class
            )
            .hasMessage(
                "Outbox event is owned by another publisher."
            );
    }

    @Test
    void shouldRejectStateChangeAfterLeaseExpiration() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        assertThatThrownBy(() ->
            processing.markPublished(
                PUBLISHER_ID,
                CREATED_AT.plusSeconds(30)
            )
        )
            .isInstanceOf(
                InvalidOutboxEventStateException.class
            )
            .hasMessage(
                "Outbox event lease has expired."
            );
    }

    @Test
    void shouldRejectRetryBeforeCurrentTime() {
        OutboxEvent processing =
            pendingEvent().claim(
                PUBLISHER_ID,
                CREATED_AT,
                LEASE_DURATION
            );

        Instant failedAt =
            CREATED_AT.plusSeconds(10);

        assertThatThrownBy(() ->
            processing.scheduleRetry(
                PUBLISHER_ID,
                failedAt,
                failedAt.minusSeconds(1),
                "Temporary failure."
            )
        )
            .isInstanceOf(
                InvalidOutboxEventException.class
            )
            .hasMessage(
                "nextAvailableAt must not be before now."
            );
    }

    private static OutboxEvent pendingEvent() {
        return OutboxEvent.pending(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            DEDUPLICATION_KEY,
            PAYLOAD,
            CREATED_AT
        );
    }

    private static OutboxEvent rehydrate(
        OutboxStatus status,
        int attemptCount,
        Instant availableAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        Instant publishedAt
    ) {
        return OutboxEvent.rehydrate(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            DEDUPLICATION_KEY,
            PAYLOAD,
            status,
            attemptCount,
            availableAt,
            lockedAt,
            lockedUntil,
            lockedBy,
            CREATED_AT,
            publishedAt,
            null
        );
    }
}
