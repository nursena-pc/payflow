package com.nursena.payflow.outbox.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.outbox.domain.exception.InvalidOutboxEventException;

public final class OutboxEvent {

    private static final int MAX_AGGREGATE_TYPE_LENGTH = 100;
    private static final int MAX_EVENT_TYPE_LENGTH = 200;
    private static final int MAX_TOPIC_LENGTH = 200;
    private static final int MAX_PARTITION_KEY_LENGTH = 100;
    private static final int MAX_DEDUPLICATION_KEY_LENGTH = 300;
    private static final int MAX_LOCKED_BY_LENGTH = 200;
    private static final int MAX_LAST_ERROR_LENGTH = 1000;

    private final UUID id;
    private final String aggregateType;
    private final UUID aggregateId;
    private final String eventType;
    private final int eventVersion;
    private final String topic;
    private final String partitionKey;
    private final String deduplicationKey;
    private final String payload;
    private final OutboxStatus status;
    private final int attemptCount;
    private final Instant availableAt;
    private final Instant lockedAt;
    private final Instant lockedUntil;
    private final String lockedBy;
    private final Instant createdAt;
    private final Instant publishedAt;
    private final String lastError;

    private OutboxEvent(
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
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );

        this.aggregateType = requireText(
            aggregateType,
            "aggregateType",
            MAX_AGGREGATE_TYPE_LENGTH
        );

        this.aggregateId = Objects.requireNonNull(
            aggregateId,
            "aggregateId must not be null"
        );

        this.eventType = requireText(
            eventType,
            "eventType",
            MAX_EVENT_TYPE_LENGTH
        );

        ensurePositiveEventVersion(eventVersion);
        this.eventVersion = eventVersion;

        this.topic = requireText(
            topic,
            "topic",
            MAX_TOPIC_LENGTH
        );

        this.partitionKey = requireText(
            partitionKey,
            "partitionKey",
            MAX_PARTITION_KEY_LENGTH
        );

        this.deduplicationKey = requireText(
            deduplicationKey,
            "deduplicationKey",
            MAX_DEDUPLICATION_KEY_LENGTH
        );

        this.payload = requirePayload(payload);

        this.status = Objects.requireNonNull(
            status,
            "status must not be null"
        );

        ensureNonNegativeAttemptCount(attemptCount);
        this.attemptCount = attemptCount;

        this.createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        this.availableAt = Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        ensureValidAvailability(
            this.availableAt,
            this.createdAt
        );

        validateProcessingState(
            status,
            lockedAt,
            lockedUntil,
            lockedBy
        );

        validatePublicationState(
            status,
            publishedAt,
            this.createdAt
        );

        this.lockedAt = lockedAt;
        this.lockedUntil = lockedUntil;
        this.lockedBy = lockedBy;
        this.publishedAt = publishedAt;

        this.lastError = optionalText(
            lastError,
            "lastError",
            MAX_LAST_ERROR_LENGTH
        );
    }

    public static OutboxEvent pending(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        String topic,
        String partitionKey,
        String deduplicationKey,
        String payload,
        Instant createdAt
    ) {
        return new OutboxEvent(
            id,
            aggregateType,
            aggregateId,
            eventType,
            eventVersion,
            topic,
            partitionKey,
            deduplicationKey,
            payload,
            OutboxStatus.PENDING,
            0,
            createdAt,
            null,
            null,
            null,
            createdAt,
            null,
            null
        );
    }

    public static OutboxEvent rehydrate(
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
        return new OutboxEvent(
            id,
            aggregateType,
            aggregateId,
            eventType,
            eventVersion,
            topic,
            partitionKey,
            deduplicationKey,
            payload,
            status,
            attemptCount,
            availableAt,
            lockedAt,
            lockedUntil,
            lockedBy,
            createdAt,
            publishedAt,
            lastError
        );
    }

    private static String requireText(
        String value,
        String fieldName,
        int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new InvalidOutboxEventException(
                fieldName + " must not be blank."
            );
        }

        if (value.length() > maximumLength) {
            throw new InvalidOutboxEventException(
                fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters."
            );
        }

        return value;
    }

    private static String optionalText(
        String value,
        String fieldName,
        int maximumLength
    ) {
        if (value == null) {
            return null;
        }

        return requireText(
            value,
            fieldName,
            maximumLength
        );
    }

    private static String requirePayload(
        String payload
    ) {
        if (payload == null
            || payload.isBlank()
            || "{}".equals(payload.trim())) {

            throw new InvalidOutboxEventException(
                "payload must contain a non-empty JSON object."
            );
        }

        return payload;
    }

    private static void ensurePositiveEventVersion(
        int eventVersion
    ) {
        if (eventVersion <= 0) {
            throw new InvalidOutboxEventException(
                "eventVersion must be positive."
            );
        }
    }

    private static void ensureNonNegativeAttemptCount(
        int attemptCount
    ) {
        if (attemptCount < 0) {
            throw new InvalidOutboxEventException(
                "attemptCount must not be negative."
            );
        }
    }

    private static void ensureValidAvailability(
        Instant availableAt,
        Instant createdAt
    ) {
        if (availableAt.isBefore(createdAt)) {
            throw new InvalidOutboxEventException(
                "availableAt must not be before createdAt."
            );
        }
    }

    private static void validateProcessingState(
        OutboxStatus status,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy
    ) {
        if (status == OutboxStatus.PROCESSING) {
            if (lockedAt == null
                || lockedUntil == null
                || lockedBy == null
                || lockedBy.isBlank()) {

                throw new InvalidOutboxEventException(
                    "PROCESSING event must have complete "
                        + "lock metadata."
                );
            }

            requireText(
                lockedBy,
                "lockedBy",
                MAX_LOCKED_BY_LENGTH
            );

            if (!lockedUntil.isAfter(lockedAt)) {
                throw new InvalidOutboxEventException(
                    "lockedUntil must be after lockedAt."
                );
            }

            return;
        }

        if (lockedAt != null
            || lockedUntil != null
            || lockedBy != null) {

            throw new InvalidOutboxEventException(
                "Only PROCESSING events may have "
                    + "lock metadata."
            );
        }
    }

    private static void validatePublicationState(
        OutboxStatus status,
        Instant publishedAt,
        Instant createdAt
    ) {
        if (status == OutboxStatus.PUBLISHED) {
            if (publishedAt == null) {
                throw new InvalidOutboxEventException(
                    "PUBLISHED event must have publishedAt."
                );
            }

            if (publishedAt.isBefore(createdAt)) {
                throw new InvalidOutboxEventException(
                    "publishedAt must not be before createdAt."
                );
            }

            return;
        }

        if (publishedAt != null) {
            throw new InvalidOutboxEventException(
                "Only PUBLISHED events may have publishedAt."
            );
        }
    }

    public UUID id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }

    public String topic() {
        return topic;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String deduplicationKey() {
        return deduplicationKey;
    }

    public String payload() {
        return payload;
    }

    public OutboxStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public Instant lockedAt() {
        return lockedAt;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public String lockedBy() {
        return lockedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public String lastError() {
        return lastError;
    }
}
