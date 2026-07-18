package com.nursena.payflow.outbox.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.exception.OutboxEventNotFoundException;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventLifecyclePersistenceAdapterTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T10:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        CREATED_AT.plusSeconds(60);

    private static final Instant TRANSITION_AT =
        CLAIMED_AT.plusSeconds(10);

    private static final Instant LOCKED_UNTIL =
        CLAIMED_AT.plusSeconds(30);

    private static final String PUBLISHER_ID =
        "publisher-1";

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final String PAYLOAD =
        """
        {
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1
        }
        """;

    @Mock
    private SpringDataOutboxEventRepository
        repository;

    private OutboxEventLifecyclePersistenceAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new OutboxEventLifecyclePersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldMarkLockedEventAsPublished() {
        OutboxEventJpaEntity entity =
            processingEntity();

        when(repository.findByIdForUpdate(
            EVENT_ID
        )).thenReturn(
            Optional.of(entity)
        );

        OutboxEvent result =
            adapter.markPublished(
                EVENT_ID,
                PUBLISHER_ID,
                TRANSITION_AT
            );

        assertThat(result.status())
            .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(result.publishedAt())
            .isEqualTo(TRANSITION_AT);

        assertThat(result.lockedAt())
            .isNull();

        assertThat(result.lockedUntil())
            .isNull();

        assertThat(result.lockedBy())
            .isNull();

        assertThat(result.lastError())
            .isNull();

        assertThat(entity.getStatus())
            .isEqualTo(OutboxStatus.PUBLISHED);

        assertThat(entity.getPublishedAt())
            .isEqualTo(TRANSITION_AT);

        assertThat(entity.getLockedBy())
            .isNull();

        verify(repository)
            .findByIdForUpdate(EVENT_ID);

        verify(repository)
            .flush();
    }

    @Test
    void shouldScheduleRetryForLockedEvent() {
        OutboxEventJpaEntity entity =
            processingEntity();

        when(repository.findByIdForUpdate(
            EVENT_ID
        )).thenReturn(
            Optional.of(entity)
        );

        Instant nextAvailableAt =
            TRANSITION_AT.plusSeconds(60);

        OutboxEvent result =
            adapter.scheduleRetry(
                EVENT_ID,
                PUBLISHER_ID,
                TRANSITION_AT,
                nextAvailableAt,
                "Kafka broker is unavailable."
            );

        assertThat(result.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(result.attemptCount())
            .isEqualTo(2);

        assertThat(result.availableAt())
            .isEqualTo(nextAvailableAt);

        assertThat(result.lockedAt())
            .isNull();

        assertThat(result.lockedUntil())
            .isNull();

        assertThat(result.lockedBy())
            .isNull();

        assertThat(result.lastError())
            .isEqualTo(
                "Kafka broker is unavailable."
            );

        assertThat(entity.getStatus())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(entity.getAvailableAt())
            .isEqualTo(nextAvailableAt);

        assertThat(entity.getLastError())
            .isEqualTo(
                "Kafka broker is unavailable."
            );

        verify(repository)
            .flush();
    }

    @Test
    void shouldMarkLockedEventAsFailed() {
        OutboxEventJpaEntity entity =
            processingEntity();

        when(repository.findByIdForUpdate(
            EVENT_ID
        )).thenReturn(
            Optional.of(entity)
        );

        OutboxEvent result =
            adapter.markFailed(
                EVENT_ID,
                PUBLISHER_ID,
                TRANSITION_AT,
                "Maximum attempts exceeded."
            );

        assertThat(result.status())
            .isEqualTo(OutboxStatus.FAILED);

        assertThat(result.attemptCount())
            .isEqualTo(2);

        assertThat(result.lockedAt())
            .isNull();

        assertThat(result.lockedUntil())
            .isNull();

        assertThat(result.lockedBy())
            .isNull();

        assertThat(result.lastError())
            .isEqualTo(
                "Maximum attempts exceeded."
            );

        assertThat(entity.getStatus())
            .isEqualTo(OutboxStatus.FAILED);

        assertThat(entity.getLastError())
            .isEqualTo(
                "Maximum attempts exceeded."
            );

        verify(repository)
            .flush();
    }

    @Test
    void shouldRejectUnknownEventBeforeFlush() {
        when(repository.findByIdForUpdate(
            EVENT_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            adapter.markPublished(
                EVENT_ID,
                PUBLISHER_ID,
                TRANSITION_AT
            )
        )
            .isInstanceOf(
                OutboxEventNotFoundException.class
            )
            .hasMessage(
                "Outbox event not found: "
                    + EVENT_ID
            );

        verify(repository, never())
            .flush();
    }

    private static OutboxEventJpaEntity
    processingEntity() {
        return new OutboxEventJpaEntity(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE + ":1:" + EVENT_ID,
            PAYLOAD,
            OutboxStatus.PROCESSING,
            2,
            CREATED_AT,
            CLAIMED_AT,
            LOCKED_UNTIL,
            PUBLISHER_ID,
            CREATED_AT,
            null,
            "Previous failure."
        );
    }
}
