package com.nursena.payflow.outbox.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.exception.DuplicateOutboxEventException;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OutboxEventPersistenceAdapterTest {

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

    private static final String DEDUPLICATION_KEY =
        EVENT_TYPE + ":1:" + TRANSACTION_ID;

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000001",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "transactionId": "60000000-0000-0000-0000-000000000001"
        }
        """;

    @Mock
    private SpringDataOutboxEventRepository repository;

    private OutboxEventPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new OutboxEventPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldSaveAndRestorePendingEvent() {
        OutboxEvent event = pendingEvent();

        when(repository.saveAndFlush(
            any(OutboxEventJpaEntity.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        OutboxEvent saved =
            adapter.save(event);

        assertThat(saved.id())
            .isEqualTo(EVENT_ID);

        assertThat(saved.aggregateType())
            .isEqualTo("PAYMENT_TRANSACTION");

        assertThat(saved.aggregateId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(saved.eventType())
            .isEqualTo(EVENT_TYPE);

        assertThat(saved.eventVersion())
            .isEqualTo(1);

        assertThat(saved.topic())
            .isEqualTo(EVENT_TYPE);

        assertThat(saved.partitionKey())
            .isEqualTo(TRANSACTION_ID.toString());

        assertThat(saved.deduplicationKey())
            .isEqualTo(DEDUPLICATION_KEY);

        assertThat(saved.payload())
            .isEqualTo(PAYLOAD);

        assertThat(saved.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(saved.attemptCount())
            .isZero();

        assertThat(saved.availableAt())
            .isEqualTo(CREATED_AT);

        assertThat(saved.createdAt())
            .isEqualTo(CREATED_AT);

        verify(repository)
            .saveAndFlush(
                any(OutboxEventJpaEntity.class)
            );
    }

    @Test
    void shouldFindAndRestoreEventById() {
        when(repository.findById(EVENT_ID))
            .thenReturn(
                Optional.of(
                    pendingEntity()
                )
            );

        Optional<OutboxEvent> result =
            adapter.findById(EVENT_ID);

        assertThat(result)
            .isPresent();

        OutboxEvent event =
            result.orElseThrow();

        assertThat(event.id())
            .isEqualTo(EVENT_ID);

        assertThat(event.aggregateId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(event.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(event.payload())
            .isEqualTo(PAYLOAD);

        verify(repository)
            .findById(EVENT_ID);
    }

    @Test
    void shouldReturnEmptyWhenEventDoesNotExist() {
        when(repository.findById(EVENT_ID))
            .thenReturn(Optional.empty());

        Optional<OutboxEvent> result =
            adapter.findById(EVENT_ID);

        assertThat(result)
            .isEmpty();

        verify(repository)
            .findById(EVENT_ID);
    }

    @Test
    void shouldTranslateDuplicateDeduplicationConstraint() {
        ConstraintViolationException violation =
            new ConstraintViolationException(
                "duplicate deduplication key",
                new SQLException(),
                "uq_outbox_events_deduplication_key"
            );

        when(repository.saveAndFlush(
            any(OutboxEventJpaEntity.class)
        )).thenThrow(
            new DataIntegrityViolationException(
                "duplicate deduplication key",
                violation
            )
        );

        assertThatThrownBy(() ->
            adapter.save(pendingEvent())
        )
            .isInstanceOf(
                DuplicateOutboxEventException.class
            )
            .hasMessage(
                "An outbox event already exists "
                    + "for the same deduplication key."
            );
    }

    @Test
    void shouldNotTranslateUnrelatedConstraintViolation() {
        ConstraintViolationException violation =
            new ConstraintViolationException(
                "unrelated constraint",
                new SQLException(),
                "some_other_constraint"
            );

        DataIntegrityViolationException databaseException =
            new DataIntegrityViolationException(
                "unrelated constraint",
                violation
            );

        when(repository.saveAndFlush(
            any(OutboxEventJpaEntity.class)
        )).thenThrow(databaseException);

        assertThatThrownBy(() ->
            adapter.save(pendingEvent())
        ).isSameAs(databaseException);
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

    private static OutboxEventJpaEntity pendingEntity() {
        return new OutboxEventJpaEntity(
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            DEDUPLICATION_KEY,
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
}
