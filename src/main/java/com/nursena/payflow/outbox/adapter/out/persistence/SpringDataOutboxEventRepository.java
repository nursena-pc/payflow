package com.nursena.payflow.outbox.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

interface SpringDataOutboxEventRepository
        extends JpaRepository<
        OutboxEventJpaEntity,
        UUID
        > {
    @Query(
            value = """
        SELECT outbox.*
        FROM outbox_events outbox
        WHERE outbox.id = :eventId
        FOR UPDATE
        """,
            nativeQuery = true
    )
    Optional<OutboxEventJpaEntity>
    findByIdForUpdate(
            @Param("eventId")
            UUID eventId
    );

    @Query(
            value = """
            SELECT outbox.*
            FROM outbox_events outbox
            WHERE (
                outbox.status = 'PENDING'
                AND outbox.available_at <= :claimedAt
            )
            OR (
                outbox.status = 'PROCESSING'
                AND outbox.locked_until <= :claimedAt
            )
            ORDER BY
                CASE
                    WHEN outbox.status = 'PENDING'
                        THEN outbox.available_at
                    ELSE outbox.locked_until
                END,
                outbox.created_at,
                outbox.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true
    )
    List<OutboxEventJpaEntity>
    findClaimableForUpdate(
            @Param("claimedAt")
            Instant claimedAt,

            @Param("batchSize")
            int batchSize
    );
}
