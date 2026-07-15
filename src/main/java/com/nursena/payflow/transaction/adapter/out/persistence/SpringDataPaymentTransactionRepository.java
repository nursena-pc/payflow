package com.nursena.payflow.transaction.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
