package com.nursena.payflow.wallet.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletUseCase;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetCurrentWalletService
    implements GetCurrentWalletUseCase {

    private final WalletRepositoryPort walletRepository;

    public GetCurrentWalletService(
        WalletRepositoryPort walletRepository
    ) {
        this.walletRepository = walletRepository;
    }

    @Override
    public GetCurrentWalletResult getCurrentWallet(
        UUID ownerId
    ) {
        Objects.requireNonNull(
            ownerId,
            "ownerId must not be null"
        );

        Wallet wallet = walletRepository
            .findByOwnerId(ownerId)
            .orElseThrow(WalletNotFoundException::new);

        return new GetCurrentWalletResult(
            wallet.id(),
            wallet.balance().amount(),
            wallet.balance().currency(),
            wallet.status(),
            wallet.createdAt()
        );
    }
}
