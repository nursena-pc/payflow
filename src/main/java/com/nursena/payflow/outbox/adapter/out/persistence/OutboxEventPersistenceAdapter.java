package com.nursena.payflow.outbox.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.outbox.application.port.out.OutboxEventRepositoryPort;
import com.nursena.payflow.outbox.domain.exception.DuplicateOutboxEventException;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class OutboxEventPersistenceAdapter
    implements OutboxEventRepositoryPort {

    private static final String DEDUPLICATION_CONSTRAINT =
        "uq_outbox_events_deduplication_key";

    private final SpringDataOutboxEventRepository repository;

    OutboxEventPersistenceAdapter(
        SpringDataOutboxEventRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public OutboxEvent save(
        OutboxEvent event
    ) {
        try {
            OutboxEventJpaEntity saved =
                repository.saveAndFlush(
                    toEntity(event)
                );

            return toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isDeduplicationConstraintViolation(
                exception
            )) {
                throw new DuplicateOutboxEventException();
            }

            throw exception;
        }
    }

    @Override
    public Optional<OutboxEvent> findById(
        UUID eventId
    ) {
        return repository
            .findById(eventId)
            .map(
                OutboxEventPersistenceAdapter::toDomain
            );
    }

    private static OutboxEventJpaEntity toEntity(
        OutboxEvent event
    ) {
        return new OutboxEventJpaEntity(
            event.id(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            event.eventVersion(),
            event.topic(),
            event.partitionKey(),
            event.deduplicationKey(),
            event.payload(),
            event.status(),
            event.attemptCount(),
            event.availableAt(),
            event.lockedAt(),
            event.lockedUntil(),
            event.lockedBy(),
            event.createdAt(),
            event.publishedAt(),
            event.lastError()
        );
    }

    private static OutboxEvent toDomain(
        OutboxEventJpaEntity entity
    ) {
        return OutboxEvent.rehydrate(
            entity.getId(),
            entity.getAggregateType(),
            entity.getAggregateId(),
            entity.getEventType(),
            entity.getEventVersion(),
            entity.getTopic(),
            entity.getPartitionKey(),
            entity.getDeduplicationKey(),
            entity.getPayload(),
            entity.getStatus(),
            entity.getAttemptCount(),
            entity.getAvailableAt(),
            entity.getLockedAt(),
            entity.getLockedUntil(),
            entity.getLockedBy(),
            entity.getCreatedAt(),
            entity.getPublishedAt(),
            entity.getLastError()
        );
    }

    private static boolean
    isDeduplicationConstraintViolation(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current
                instanceof ConstraintViolationException violation) {

                return DEDUPLICATION_CONSTRAINT.equals(
                    violation.getConstraintName()
                );
            }

            current = current.getCause();
        }

        return false;
    }
}
