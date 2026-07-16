package com.nursena.payflow.transaction.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPaymentTransactionRepository
    extends JpaRepository<
    PaymentTransactionJpaEntity,
    UUID
    > {

    Optional<PaymentTransactionJpaEntity>
    findBySourceWalletIdAndIdempotencyKey(
        UUID sourceWalletId,
        String idempotencyKey
    );

    @Query("""
    select paymentTransaction
    from PaymentTransactionJpaEntity paymentTransaction
    where (
        (
            :includeOutgoing = true
            and paymentTransaction.sourceWalletId = :walletId
        )
        or
        (
            :includeIncoming = true
            and paymentTransaction.targetWalletId = :walletId
        )
    )
    and (
        :filterByStatus = false
        or paymentTransaction.status = :status
    )
    and (
        :filterByFrom = false
        or paymentTransaction.createdAt >= :fromInclusive
    )
    and (
        :filterByTo = false
        or paymentTransaction.createdAt < :toExclusive
    )
    """)
    Page<PaymentTransactionJpaEntity> findHistory(
        @Param("walletId")
        UUID walletId,

        @Param("includeOutgoing")
        boolean includeOutgoing,

        @Param("includeIncoming")
        boolean includeIncoming,

        @Param("filterByStatus")
        boolean filterByStatus,

        @Param("status")
        TransactionStatus status,

        @Param("filterByFrom")
        boolean filterByFrom,

        @Param("fromInclusive")
        Instant fromInclusive,

        @Param("filterByTo")
        boolean filterByTo,

        @Param("toExclusive")
        Instant toExclusive,

        Pageable pageable
    );
}
