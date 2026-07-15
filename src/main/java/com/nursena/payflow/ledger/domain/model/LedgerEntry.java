package com.nursena.payflow.ledger.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.exception.InvalidLedgerAmountException;
import com.nursena.payflow.wallet.domain.model.Money;

public final class LedgerEntry {

    private final UUID id;
    private final UUID transactionId;
    private final UUID walletId;
    private final LedgerEntryType type;
    private final Money amount;
    private final Instant createdAt;

    private LedgerEntry(
        UUID id,
        UUID transactionId,
        UUID walletId,
        LedgerEntryType type,
        Money amount,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );
        this.transactionId = Objects.requireNonNull(
            transactionId,
            "transactionId must not be null"
        );
        this.walletId = Objects.requireNonNull(
            walletId,
            "walletId must not be null"
        );
        this.type = Objects.requireNonNull(
            type,
            "type must not be null"
        );
        this.amount = Objects.requireNonNull(
            amount,
            "amount must not be null"
        );
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        if (!amount.isPositive()) {
            throw new InvalidLedgerAmountException();
        }
    }

    public static LedgerEntry create(
        UUID transactionId,
        UUID walletId,
        LedgerEntryType type,
        Money amount,
        Instant now
    ) {
        return new LedgerEntry(
            UUID.randomUUID(),
            transactionId,
            walletId,
            type,
            amount,
            now
        );
    }

    public static LedgerEntry rehydrate(
        UUID id,
        UUID transactionId,
        UUID walletId,
        LedgerEntryType type,
        Money amount,
        Instant createdAt
    ) {
        return new LedgerEntry(
            id,
            transactionId,
            walletId,
            type,
            amount,
            createdAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public UUID walletId() {
        return walletId;
    }

    public LedgerEntryType type() {
        return type;
    }

    public Money amount() {
        return amount;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
