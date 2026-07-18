package com.nursena.payflow.outbox.adapter.out.persistence;

import com.nursena.payflow.outbox.domain.model.OutboxEvent;

final class OutboxEventPersistenceMapper {

    private OutboxEventPersistenceMapper() {
    }

    static OutboxEventJpaEntity toEntity(
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

    static OutboxEvent toDomain(
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
}
