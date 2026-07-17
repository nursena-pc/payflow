package com.nursena.payflow.wallet.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.model.Wallet;

public interface WalletRepositoryPort {

    boolean existsByOwnerId(UUID ownerId);

    Optional<Wallet> findByOwnerId(UUID ownerId);

    Optional<Wallet> findById(UUID walletId);

    Wallet save(Wallet wallet);

    Wallet update(Wallet wallet);
}
