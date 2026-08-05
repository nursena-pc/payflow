package com.nursena.payflow.maildelivery.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMailOutboxMessageRepository
    extends JpaRepository<MailOutboxMessageJpaEntity, UUID> {

    @Query(
        value = """
        SELECT mail.*
        FROM mail_outbox_messages mail
        WHERE mail.id = :messageId
        FOR UPDATE
        """,
        nativeQuery = true
    )
    Optional<MailOutboxMessageJpaEntity> findByIdForUpdate(
        @Param("messageId") UUID messageId
    );

    @Query(
        value = """
        SELECT mail.*
        FROM mail_outbox_messages mail
        WHERE mail.user_id = :userId
          AND mail.purpose = :purpose
          AND mail.status IN ('PENDING', 'PROCESSING')
        ORDER BY mail.created_at, mail.id
        FOR UPDATE
        """,
        nativeQuery = true
    )
    List<MailOutboxMessageJpaEntity> findUnresolvedForUpdate(
        @Param("userId") UUID userId,
        @Param("purpose") String purpose
    );

    @Query(
        value = """
        SELECT mail.*
        FROM mail_outbox_messages mail
        WHERE (
            (
                mail.status = 'PENDING'
                AND mail.available_at <= :claimedAt
            )
            OR
            (
                mail.status = 'PROCESSING'
                AND mail.locked_until <= :claimedAt
            )
        )
          AND mail.expires_at > :claimedAt
        ORDER BY
            CASE
                WHEN mail.status = 'PENDING' THEN mail.available_at
                ELSE mail.locked_until
            END,
            mail.created_at,
            mail.id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    List<MailOutboxMessageJpaEntity> findClaimableForUpdate(
        @Param("claimedAt") Instant claimedAt,
        @Param("batchSize") int batchSize
    );

    @Query(
        value = """
        SELECT mail.*
        FROM mail_outbox_messages mail
        WHERE mail.status IN ('PENDING', 'PROCESSING')
          AND mail.expires_at <= :expiredAt
          AND (
              mail.status = 'PENDING'
              OR mail.locked_until <= :expiredAt
          )
        ORDER BY mail.expires_at, mail.created_at, mail.id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    List<MailOutboxMessageJpaEntity> findExpiredForUpdate(
        @Param("expiredAt") Instant expiredAt,
        @Param("batchSize") int batchSize
    );
}
