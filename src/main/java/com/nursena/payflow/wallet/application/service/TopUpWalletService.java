package com.nursena.payflow.wallet.application.service;

import java.util.Objects;

import com.nursena.payflow.wallet.application.port.in.TopUpWalletCommand;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletUseCase;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpWalletService
    implements TopUpWalletUseCase {

    private final WalletRepositoryPort walletRepository;

    public TopUpWalletService(
        WalletRepositoryPort walletRepository
    ) {
        this.walletRepository = walletRepository;
    }

    @Override
    @Transactional
    public TopUpWalletResult topUp(
        TopUpWalletCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        Wallet wallet = walletRepository
            .findByOwnerId(command.ownerId())
            .orElseThrow(WalletNotFoundException::new);

        Money amount = new Money(
            command.amount(),
            wallet.balance().currency()
        );

        wallet.credit(amount);

        Wallet updatedWallet =
            walletRepository.update(wallet);

        return TopUpWalletResult.from(
            updatedWallet
        );
    }
}
