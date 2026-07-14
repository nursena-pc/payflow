package com.nursena.payflow.wallet.application.service;

import java.time.Clock;

import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.application.port.in.OpenWalletUseCase;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletAlreadyExistsException;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenWalletService
    implements OpenWalletUseCase {

    private final WalletRepositoryPort walletRepository;
    private final Clock clock;

    public OpenWalletService(
        WalletRepositoryPort walletRepository,
        Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OpenWalletResult open(OpenWalletCommand command) {
        if (walletRepository.existsByOwnerId(
            command.ownerId()
        )) {
            throw new WalletAlreadyExistsException();
        }

        Wallet wallet = Wallet.open(
            command.ownerId(),
            command.currency(),
            clock.instant()
        );

        Wallet savedWallet =
            walletRepository.save(wallet);

        return OpenWalletResult.from(savedWallet);
    }
}
