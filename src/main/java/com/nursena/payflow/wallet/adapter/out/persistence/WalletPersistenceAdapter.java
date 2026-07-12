package com.nursena.payflow.wallet.adapter.out.persistence;

import java.time.Instant;

import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Component;

@Component
class WalletPersistenceAdapter implements WalletRepositoryPort {

    private final SpringDataWalletRepository repository;

    WalletPersistenceAdapter(SpringDataWalletRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByOwnerId(java.util.UUID ownerId) {
        return repository.existsByOwnerId(ownerId);
    }

    @Override
    public Wallet save(Wallet wallet) {
        Instant now = Instant.now();
        WalletJpaEntity entity = new WalletJpaEntity(
                wallet.id(),
                wallet.ownerId(),
                wallet.balance().amount(),
                wallet.balance().currency(),
                wallet.status(),
                wallet.createdAt(),
                now
        );
        WalletJpaEntity saved = repository.save(entity);
        return Wallet.rehydrate(
                saved.getId(),
                saved.getOwnerId(),
                new Money(saved.getBalance(), saved.getCurrency()),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }
}
