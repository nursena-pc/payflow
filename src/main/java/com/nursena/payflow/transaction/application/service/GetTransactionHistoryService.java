package com.nursena.payflow.transaction.application.service;

import java.util.Objects;

import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryQuery;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryUseCase;
import com.nursena.payflow.transaction.application.port.out.TransactionHistoryQueryPort;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Wallet;

public final class GetTransactionHistoryService
    implements GetTransactionHistoryUseCase {

    private final WalletRepositoryPort walletRepository;
    private final TransactionHistoryQueryPort
        transactionHistoryQueryPort;

    public GetTransactionHistoryService(
        WalletRepositoryPort walletRepository,
        TransactionHistoryQueryPort
            transactionHistoryQueryPort
    ) {
        this.walletRepository = Objects.requireNonNull(
            walletRepository,
            "walletRepository must not be null"
        );

        this.transactionHistoryQueryPort =
            Objects.requireNonNull(
                transactionHistoryQueryPort,
                "transactionHistoryQueryPort "
                    + "must not be null"
            );
    }

    @Override
    public TransactionHistoryPage
    getTransactionHistory(
        GetTransactionHistoryQuery query
    ) {
        Objects.requireNonNull(
            query,
            "query must not be null"
        );

        Wallet wallet = walletRepository
            .findByOwnerId(query.ownerId())
            .orElseThrow(
                WalletNotFoundException::new
            );

        return transactionHistoryQueryPort
            .findByWalletId(
                wallet.id(),
                query.page(),
                query.size()
            );
    }
}
