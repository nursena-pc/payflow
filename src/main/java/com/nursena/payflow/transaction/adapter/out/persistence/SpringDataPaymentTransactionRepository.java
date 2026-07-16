package com.nursena.payflow.transaction.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

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
        where paymentTransaction.sourceWalletId = :walletId
           or paymentTransaction.targetWalletId = :walletId
        """)
    Page<PaymentTransactionJpaEntity>
    findHistoryByWalletId(
        @Param("walletId") UUID walletId,
        Pageable pageable
    );
}
