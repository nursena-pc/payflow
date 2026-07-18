package com.nursena.payflow.transaction.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.time.temporal.ChronoUnit;

import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.exception.IdempotencyRequestInProgressException;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.ledger.application.port.out.LedgerRepositoryPort;
import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import com.nursena.payflow.transaction.application.port.out.PaymentTransactionRepositoryPort;
import com.nursena.payflow.transaction.domain.exception.TransferCurrencyMismatchException;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import com.nursena.payflow.transaction.application.port.out.TransferCompletedEventRecorderPort;

@Service
public class TransferMoneyService
    implements TransferMoneyUseCase {

    private final TransferCompletedEventRecorderPort
        completedEventRecorder;
    private final WalletRepositoryPort walletRepository;
    private final PaymentTransactionRepositoryPort
        transactionRepository;
    private final LedgerRepositoryPort ledgerRepository;
    private final Clock clock;

    public TransferMoneyService(
        WalletRepositoryPort walletRepository,
        PaymentTransactionRepositoryPort transactionRepository,
        LedgerRepositoryPort ledgerRepository,
        TransferCompletedEventRecorderPort completedEventRecorder,
        Clock clock
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.completedEventRecorder =
            completedEventRecorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TransferMoneyResult transfer(
        TransferMoneyCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        Wallet sourceWallet = walletRepository
            .findByOwnerId(command.ownerId())
            .orElseThrow(WalletNotFoundException::new);

        Money amount = new Money(
            command.amount(),
            sourceWallet.balance().currency()
        );

        IdempotencyKey idempotencyKey =
            new IdempotencyKey(
                command.idempotencyKey()
            );

        Optional<PaymentTransaction> existingTransaction =
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    sourceWallet.id(),
                    idempotencyKey
                );

        if (existingTransaction.isPresent()) {
            return replayExistingTransaction(
                existingTransaction.orElseThrow(),
                command.targetWalletId(),
                amount
            );
        }

        Wallet targetWallet = walletRepository
            .findById(command.targetWalletId())
            .orElseThrow(WalletNotFoundException::new);

        ensureMatchingCurrencies(
            sourceWallet,
            targetWallet
        );

        Instant createdAt = currentTime();

        PaymentTransaction transaction =
            PaymentTransaction.startTransfer(
                sourceWallet.id(),
                targetWallet.id(),
                amount,
                idempotencyKey,
                createdAt
            );

        sourceWallet.debit(amount);
        targetWallet.credit(amount);

        PaymentTransaction savedTransaction =
            transactionRepository.save(transaction);

        updateWalletsInDeterministicOrder(
            sourceWallet,
            targetWallet
        );

        DoubleEntryLedger ledger =
            DoubleEntryLedger.forTransfer(
                savedTransaction.id(),
                sourceWallet.id(),
                targetWallet.id(),
                amount,
                createdAt
            );

        ledgerRepository.save(ledger);

        savedTransaction.complete(currentTime());

        PaymentTransaction completedTransaction =
            transactionRepository.update(
                savedTransaction
            );

        completedEventRecorder.record(
            TransferCompletedEvent.from(
                completedTransaction
            )
        );

        return TransferMoneyResult.from(
            completedTransaction
        );
    }

    private static TransferMoneyResult
    replayExistingTransaction(
        PaymentTransaction transaction,
        UUID targetWalletId,
        Money amount
    ) {

        if (!transaction.matchesTransferRequest(
            targetWalletId,
            amount
        )) {
            throw new IdempotencyConflictException();
        }

        if (transaction.status()
            != TransactionStatus.COMPLETED) {
            throw new IdempotencyRequestInProgressException();
        }

        return TransferMoneyResult.from(transaction);
    }

    private void updateWalletsInDeterministicOrder(
        Wallet firstWallet,
        Wallet secondWallet
    ) {
        List.of(firstWallet, secondWallet)
            .stream()
            .sorted(
                Comparator.comparing(
                    wallet -> wallet.id().toString()
                )
            )
            .forEach(walletRepository::update);
    }

    private static void ensureMatchingCurrencies(
        Wallet sourceWallet,
        Wallet targetWallet
    ) {
        if (sourceWallet.balance().currency()
            != targetWallet.balance().currency()) {
            throw new TransferCurrencyMismatchException();
        }
    }
    private Instant currentTime() {
        return clock.instant()
            .truncatedTo(ChronoUnit.MICROS);
    }
}
