package com.nursena.payflow.outbox.adapter.out.persistence;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxEventClaimPersistenceAdapter
    implements OutboxEventClaimPort {

    private static final int
        MAX_PUBLISHER_ID_LENGTH = 200;

    private final SpringDataOutboxEventRepository
        repository;

    OutboxEventClaimPersistenceAdapter(
        SpringDataOutboxEventRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public List<OutboxEvent> claimAvailable(
        String publisherId,
        Instant claimedAt,
        Duration leaseDuration,
        int batchSize
    ) {
        validateRequest(
            publisherId,
            claimedAt,
            leaseDuration,
            batchSize
        );

        List<OutboxEventJpaEntity> entities =
            repository.findClaimableForUpdate(
                claimedAt,
                batchSize
            );

        if (entities.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> claimedEvents =
            new ArrayList<>(entities.size());

        for (
            OutboxEventJpaEntity entity
            : entities
        ) {
            OutboxEvent currentEvent =
                OutboxEventPersistenceMapper
                    .toDomain(entity);

            OutboxEvent claimedEvent =
                currentEvent.claim(
                    publisherId,
                    claimedAt,
                    leaseDuration
                );

            entity.applyDeliveryState(
                claimedEvent
            );

            claimedEvents.add(
                claimedEvent
            );
        }

        repository.flush();

        return List.copyOf(
            claimedEvents
        );
    }

    private static void validateRequest(
        String publisherId,
        Instant claimedAt,
        Duration leaseDuration,
        int batchSize
    ) {
        if (
            publisherId == null
                || publisherId.isBlank()
        ) {
            throw new IllegalArgumentException(
                "publisherId must not be blank."
            );
        }

        if (
            publisherId.length()
                > MAX_PUBLISHER_ID_LENGTH
        ) {
            throw new IllegalArgumentException(
                "publisherId must not exceed "
                    + MAX_PUBLISHER_ID_LENGTH
                    + " characters."
            );
        }

        Objects.requireNonNull(
            claimedAt,
            "claimedAt must not be null"
        );

        Objects.requireNonNull(
            leaseDuration,
            "leaseDuration must not be null"
        );

        if (
            leaseDuration.isZero()
                || leaseDuration.isNegative()
        ) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive."
            );
        }

        try {
            claimedAt.plus(
                leaseDuration
            );
        } catch (
            DateTimeException
            | ArithmeticException exception
        ) {
            throw new IllegalArgumentException(
                "leaseDuration produces "
                    + "an invalid lease end.",
                exception
            );
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive."
            );
        }
    }
}
