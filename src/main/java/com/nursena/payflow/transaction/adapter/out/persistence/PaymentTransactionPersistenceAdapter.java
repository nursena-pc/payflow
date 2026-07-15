package com.nursena.payflow.transaction.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.transaction.application.port.out.PaymentTransactionRepositoryPort;
import com.nursena.payflow.transaction.domain.exception.PaymentTransactionNotFoundException;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.wallet.domain.model.Money;
import org.springframework.stereotype.Component;

@Component
class PaymentTransactionPersistenceAdapter
    implements PaymentTransactionRepositoryPort {

    private final SpringDataPaymentTransactionRepository
        repository;

    PaymentTransactionPersistenceAdapter(
        SpringDataPaymentTransactionRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentTransaction>
    findBySourceWalletIdAndIdempotencyKey(
        UUID sourceWalletId,
        IdempotencyKey idempotencyKey
    ) {

        return repository
            .findBySourceWalletIdAndIdempotencyKey(
                sourceWalletId,
                idempotencyKey.value()
            )
            .map(
                PaymentTransactionPersistenceAdapter
                    ::toDomain
            );
    }

    @Override
    public PaymentTransaction save(
        PaymentTransaction transaction
    ) {
        PaymentTransactionJpaEntity saved =
            repository.saveAndFlush(
                toEntity(transaction)
            );

        return toDomain(saved);
    }

    @Override
    public PaymentTransaction update(
        PaymentTransaction transaction
    ) {
        PaymentTransactionJpaEntity entity =
            repository
                .findById(transaction.id())
                .orElseThrow(
                    PaymentTransactionNotFoundException::new
                );

        entity.updateState(
            transaction.status(),
            transaction.completedAt()
        );

        repository.flush();

        return toDomain(entity);
    }

    private static PaymentTransactionJpaEntity toEntity(
        PaymentTransaction transaction
    ) {
        return new PaymentTransactionJpaEntity(
            transaction.id(),
            transaction.sourceWalletId(),
            transaction.targetWalletId(),
            transaction.type(),
            transaction.status(),
            transaction.amount().amount(),
            transaction.amount().currency(),
            transaction.idempotencyKey().value(),
            null,
            transaction.createdAt(),
            transaction.completedAt()
        );
    }

    private static PaymentTransaction toDomain(
        PaymentTransactionJpaEntity entity
    ) {
        return PaymentTransaction.rehydrate(
            entity.getId(),
            entity.getSourceWalletId(),
            entity.getTargetWalletId(),
            new Money(
                entity.getAmount(),
                entity.getCurrency()
            ),
            new IdempotencyKey(
                entity.getIdempotencyKey()
            ),
            entity.getTransactionType(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getCompletedAt()
        );
    }
}
