package com.nursena.payflow.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.exception.InvalidLedgerEntryPairException;
import com.nursena.payflow.wallet.domain.model.Money;

public record DoubleEntryLedger(
    LedgerEntry debitEntry,
    LedgerEntry creditEntry
) {

    public DoubleEntryLedger {
        Objects.requireNonNull(
            debitEntry,
            "debitEntry must not be null"
        );
        Objects.requireNonNull(
            creditEntry,
            "creditEntry must not be null"
        );

        boolean validTypes =
            debitEntry.type() == LedgerEntryType.DEBIT
                && creditEntry.type()
                == LedgerEntryType.CREDIT;

        boolean sameTransaction =
            debitEntry.transactionId().equals(
                creditEntry.transactionId()
            );

        boolean differentWallets =
            !debitEntry.walletId().equals(
                creditEntry.walletId()
            );

        boolean balancedAmount =
            debitEntry.amount().equals(
                creditEntry.amount()
            );

        if (!validTypes
            || !sameTransaction
            || !differentWallets
            || !balancedAmount) {
            throw new InvalidLedgerEntryPairException();
        }
    }

    public static DoubleEntryLedger forTransfer(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId,
        Money amount,
        Instant now
    ) {
        Objects.requireNonNull(
            transactionId,
            "transactionId must not be null"
        );
        Objects.requireNonNull(
            sourceWalletId,
            "sourceWalletId must not be null"
        );
        Objects.requireNonNull(
            targetWalletId,
            "targetWalletId must not be null"
        );
        Objects.requireNonNull(
            amount,
            "amount must not be null"
        );
        Objects.requireNonNull(
            now,
            "now must not be null"
        );

        LedgerEntry debitEntry =
            LedgerEntry.create(
                transactionId,
                sourceWalletId,
                LedgerEntryType.DEBIT,
                amount,
                now
            );

        LedgerEntry creditEntry =
            LedgerEntry.create(
                transactionId,
                targetWalletId,
                LedgerEntryType.CREDIT,
                amount,
                now
            );

        return new DoubleEntryLedger(
            debitEntry,
            creditEntry
        );
    }
}
