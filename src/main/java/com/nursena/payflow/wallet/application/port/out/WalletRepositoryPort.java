package com.nursena.payflow.wallet.application.port.out;

import java.util.UUID;

import com.nursena.payflow.wallet.domain.model.Wallet;

public interface WalletRepositoryPort {
    boolean existsByOwnerId(UUID ownerId);

    Wallet save(Wallet wallet);
}
