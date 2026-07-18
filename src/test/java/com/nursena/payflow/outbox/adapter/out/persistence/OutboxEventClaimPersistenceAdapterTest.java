package com.nursena.payflow.outbox.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimPersistenceAdapterTest {

    private static final UUID FIRST_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000002"
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

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

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

    private OutboxEventClaimPersistenceAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new OutboxEventClaimPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldClaimSelectedEventsInRepositoryOrder() {
        OutboxEventJpaEntity pending =
            pendingEntity();

        OutboxEventJpaEntity expiredProcessing =
            expiredProcessingEntity();

        when(repository.findClaimableForUpdate(
            CLAIMED_AT,
            2
        )).thenReturn(
            List.of(
                pending,
                expiredProcessing
            )
        );

        List<OutboxEvent> claimed =
            adapter.claimAvailable(
                "publisher-2",
                CLAIMED_AT,
                LEASE_DURATION,
                2
            );

        assertThat(claimed)
            .extracting(
                OutboxEvent::id
            )
            .containsExactly(
                FIRST_EVENT_ID,
                SECOND_EVENT_ID
            );

        OutboxEvent first =
            claimed.get(0);

        assertThat(first.status())
            .isEqualTo(
                OutboxStatus.PROCESSING
            );

        assertThat(first.attemptCount())
            .isEqualTo(1);

        assertThat(first.lockedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(first.lockedUntil())
            .isEqualTo(
                CLAIMED_AT.plusSeconds(30)
            );

        assertThat(first.lockedBy())
            .isEqualTo("publisher-2");

        OutboxEvent second =
            claimed.get(1);

        assertThat(second.status())
            .isEqualTo(
                OutboxStatus.PROCESSING
            );

        assertThat(second.attemptCount())
            .isEqualTo(2);

        assertThat(second.lockedBy())
            .isEqualTo("publisher-2");

        assertThat(second.lastError())
            .isEqualTo(
                "Previous publishing failure."
            );

        assertThat(pending.getStatus())
            .isEqualTo(
                OutboxStatus.PROCESSING
            );

        assertThat(pending.getAttemptCount())
            .isEqualTo(1);

        assertThat(
            expiredProcessing.getStatus()
        ).isEqualTo(
            OutboxStatus.PROCESSING
        );

        assertThat(
            expiredProcessing.getAttemptCount()
        ).isEqualTo(2);

        assertThat(
            expiredProcessing.getLockedBy()
        ).isEqualTo("publisher-2");

        verify(repository)
            .findClaimableForUpdate(
                CLAIMED_AT,
                2
            );

        verify(repository)
            .flush();
    }

    @Test
    void shouldReturnEmptyWithoutFlushing() {
        when(repository.findClaimableForUpdate(
            CLAIMED_AT,
            10
        )).thenReturn(List.of());

        List<OutboxEvent> result =
            adapter.claimAvailable(
                "publisher-1",
                CLAIMED_AT,
                LEASE_DURATION,
                10
            );

        assertThat(result)
            .isEmpty();

        verify(repository)
            .findClaimableForUpdate(
                CLAIMED_AT,
                10
            );
    }

    @Test
    void shouldRejectInvalidBatchSizeBeforeQuery() {
        assertThatThrownBy(() ->
            adapter.claimAvailable(
                "publisher-1",
                CLAIMED_AT,
                LEASE_DURATION,
                0
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "batchSize must be positive."
            );

        verifyNoInteractions(repository);
    }

    private static OutboxEventJpaEntity
    pendingEntity() {
        return new OutboxEventJpaEntity(
            FIRST_EVENT_ID,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE
                + ":1:"
                + FIRST_EVENT_ID,
            PAYLOAD,
            OutboxStatus.PENDING,
            0,
            CREATED_AT,
            null,
            null,
            null,
            CREATED_AT,
            null,
            null
        );
    }

    private static OutboxEventJpaEntity
    expiredProcessingEntity() {
        return new OutboxEventJpaEntity(
            SECOND_EVENT_ID,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE
                + ":1:"
                + SECOND_EVENT_ID,
            PAYLOAD,
            OutboxStatus.PROCESSING,
            1,
            CREATED_AT,
            CREATED_AT.plusSeconds(10),
            CREATED_AT.plusSeconds(30),
            "publisher-1",
            CREATED_AT,
            null,
            "Previous publishing failure."
        );
    }
}
