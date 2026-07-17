package com.nursena.payflow.outbox.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "aggregate_type",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String aggregateType;

    @Column(
        name = "aggregate_id",
        nullable = false,
        updatable = false
    )
    private UUID aggregateId;

    @Column(
        name = "event_type",
        nullable = false,
        updatable = false,
        length = 200
    )
    private String eventType;

    @Column(
        name = "event_version",
        nullable = false,
        updatable = false
    )
    private int eventVersion;

    @Column(
        nullable = false,
        updatable = false,
        length = 200
    )
    private String topic;

    @Column(
        name = "partition_key",
        nullable = false,
        updatable = false,
        length = 100
    )
    private String partitionKey;

    @Column(
        name = "deduplication_key",
        nullable = false,
        updatable = false,
        unique = true,
        length = 300
    )
    private String deduplicationKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        nullable = false,
        updatable = false,
        columnDefinition = "jsonb"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 20
    )
    private OutboxStatus status;

    @Column(
        name = "attempt_count",
        nullable = false
    )
    private int attemptCount;

    @Column(
        name = "available_at",
        nullable = false
    )
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(
        name = "locked_by",
        length = 200
    )
    private String lockedBy;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(
        name = "last_error",
        length = 1000
    )
    private String lastError;

    protected OutboxEventJpaEntity() {
    }

    OutboxEventJpaEntity(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        String topic,
        String partitionKey,
        String deduplicationKey,
        String payload,
        OutboxStatus status,
        int attemptCount,
        Instant availableAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        Instant createdAt,
        Instant publishedAt,
        String lastError
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.deduplicationKey = deduplicationKey;
        this.payload = payload;
        this.status = status;
        this.attemptCount = attemptCount;
        this.availableAt = availableAt;
        this.lockedAt = lockedAt;
        this.lockedUntil = lockedUntil;
        this.lockedBy = lockedBy;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
    }

    UUID getId() {
        return id;
    }

    String getAggregateType() {
        return aggregateType;
    }

    UUID getAggregateId() {
        return aggregateId;
    }

    String getEventType() {
        return eventType;
    }

    int getEventVersion() {
        return eventVersion;
    }

    String getTopic() {
        return topic;
    }

    String getPartitionKey() {
        return partitionKey;
    }

    String getDeduplicationKey() {
        return deduplicationKey;
    }

    String getPayload() {
        return payload;
    }

    OutboxStatus getStatus() {
        return status;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    Instant getAvailableAt() {
        return availableAt;
    }

    Instant getLockedAt() {
        return lockedAt;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    String getLockedBy() {
        return lockedBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getPublishedAt() {
        return publishedAt;
    }

    String getLastError() {
        return lastError;
    }
}
