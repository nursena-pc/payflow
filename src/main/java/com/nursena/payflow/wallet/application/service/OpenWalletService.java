package com.nursena.payflow.wallet.application.service;

import java.time.Clock;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletUseCase;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenWalletService implements OpenWalletUseCase {

    private final WalletRepositoryPort walletRepository;
    private final Clock clock;

    public OpenWalletService(WalletRepositoryPort walletRepository) {
        this(walletRepository, Clock.systemUTC());
    }

    OpenWalletService(WalletRepositoryPort walletRepository, Clock clock) {
        this.walletRepository = walletRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID open(OpenWalletCommand command) {
        if (walletRepository.existsByOwnerId(command.ownerId())) {
            throw new IllegalStateException("Owner already has a wallet.");
        }

        Wallet wallet = Wallet.open(command.ownerId(), command.currency(), clock.instant());
        return walletRepository.save(wallet).id();
    }
}
