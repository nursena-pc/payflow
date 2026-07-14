package com.nursena.payflow.wallet.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletAlreadyExistsException;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class WalletPersistenceAdapter
    implements WalletRepositoryPort {

    private static final String OWNER_UNIQUE_CONSTRAINT =
        "uq_wallets_owner_id";

    private final SpringDataWalletRepository repository;
    private final Clock clock;

    WalletPersistenceAdapter(
        SpringDataWalletRepository repository,
        Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public boolean existsByOwnerId(UUID ownerId) {
        return repository.existsByOwnerId(ownerId);
    }

    @Override
    public Optional<Wallet> findByOwnerId(UUID ownerId) {
        return repository
            .findByOwnerId(ownerId)
            .map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Wallet save(Wallet wallet) {
        Instant now = clock.instant();

        WalletJpaEntity entity = new WalletJpaEntity(
            wallet.id(),
            wallet.ownerId(),
            wallet.balance().amount(),
            wallet.balance().currency(),
            wallet.status(),
            wallet.createdAt(),
            now
        );

        try {
            WalletJpaEntity saved =
                repository.saveAndFlush(entity);

            return toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isOwnerUniqueConstraintViolation(exception)) {
                throw new WalletAlreadyExistsException();
            }

            throw exception;
        }
    }

    private static boolean isOwnerUniqueConstraintViolation(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current
                instanceof ConstraintViolationException violation) {
                return OWNER_UNIQUE_CONSTRAINT.equals(
                    violation.getConstraintName()
                );
            }

            current = current.getCause();
        }

        return false;
    }

    private static Wallet toDomain(
        WalletJpaEntity entity
    ) {
        return Wallet.rehydrate(
            entity.getId(),
            entity.getOwnerId(),
            new Money(
                entity.getBalance(),
                entity.getCurrency()
            ),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
