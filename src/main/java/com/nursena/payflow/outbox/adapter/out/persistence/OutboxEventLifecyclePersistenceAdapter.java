package com.nursena.payflow.outbox.adapter.out.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.nursena.payflow.outbox.application.port.out.OutboxEventLifecyclePort;
import com.nursena.payflow.outbox.domain.exception.OutboxEventNotFoundException;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxEventLifecyclePersistenceAdapter
    implements OutboxEventLifecyclePort {

    private final SpringDataOutboxEventRepository
        repository;

    OutboxEventLifecyclePersistenceAdapter(
        SpringDataOutboxEventRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OutboxEvent markPublished(
        UUID eventId,
        String publisherId,
        Instant publishedAt
    ) {
        return transition(
            eventId,
            event -> event.markPublished(
                publisherId,
                publishedAt
            )
        );
    }

    @Override
    @Transactional
    public OutboxEvent scheduleRetry(
        UUID eventId,
        String publisherId,
        Instant failedAt,
        Instant nextAvailableAt,
        String error
    ) {
        return transition(
            eventId,
            event -> event.scheduleRetry(
                publisherId,
                failedAt,
                nextAvailableAt,
                error
            )
        );
    }

    @Override
    @Transactional
    public OutboxEvent markFailed(
        UUID eventId,
        String publisherId,
        Instant failedAt,
        String error
    ) {
        return transition(
            eventId,
            event -> event.markFailed(
                publisherId,
                failedAt,
                error
            )
        );
    }

    private OutboxEvent transition(
        UUID eventId,
        UnaryOperator<OutboxEvent> operation
    ) {
        UUID validatedEventId =
            Objects.requireNonNull(
                eventId,
                "eventId must not be null"
            );

        OutboxEventJpaEntity entity =
            repository
                .findByIdForUpdate(
                    validatedEventId
                )
                .orElseThrow(() ->
                    new OutboxEventNotFoundException(
                        validatedEventId
                    )
                );

        OutboxEvent currentEvent =
            OutboxEventPersistenceMapper
                .toDomain(entity);

        OutboxEvent updatedEvent =
            operation.apply(currentEvent);

        entity.applyDeliveryState(
            updatedEvent
        );

        repository.flush();

        return updatedEvent;
    }
}
