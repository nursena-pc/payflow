package com.nursena.payflow.transaction.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.exception.InvalidTransactionStateException;
import com.nursena.payflow.transaction.domain.exception.InvalidTransferAmountException;
import com.nursena.payflow.transaction.domain.exception.SelfTransferNotAllowedException;
import com.nursena.payflow.wallet.domain.model.Money;

public final class PaymentTransaction {

    private final UUID id;
    private final UUID sourceWalletId;
    private final UUID targetWalletId;
    private final Money amount;
    private final IdempotencyKey idempotencyKey;
    private final TransactionType type;
    private final Instant createdAt;

    private TransactionStatus status;
    private Instant completedAt;

    private PaymentTransaction(
        UUID id,
        UUID sourceWalletId,
        UUID targetWalletId,
        Money amount,
        IdempotencyKey idempotencyKey,
        TransactionType type,
        TransactionStatus status,
        Instant createdAt,
        Instant completedAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );
        this.sourceWalletId = Objects.requireNonNull(
            sourceWalletId,
            "sourceWalletId must not be null"
        );
        this.targetWalletId = Objects.requireNonNull(
            targetWalletId,
            "targetWalletId must not be null"
        );
        this.amount = Objects.requireNonNull(
            amount,
            "amount must not be null"
        );
        this.idempotencyKey = Objects.requireNonNull(
            idempotencyKey,
            "idempotencyKey must not be null"
        );
        this.type = Objects.requireNonNull(
            type,
            "type must not be null"
        );
        this.status = Objects.requireNonNull(
            status,
            "status must not be null"
        );
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );
        this.completedAt = completedAt;
    }

    public static PaymentTransaction startTransfer(
        UUID sourceWalletId,
        UUID targetWalletId,
        Money amount,
        IdempotencyKey idempotencyKey,
        Instant now
    ) {
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
            idempotencyKey,
            "idempotencyKey must not be null"
        );
        Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (sourceWalletId.equals(targetWalletId)) {
            throw new SelfTransferNotAllowedException();
        }

        if (!amount.isPositive()) {
            throw new InvalidTransferAmountException();
        }

        return new PaymentTransaction(
            UUID.randomUUID(),
            sourceWalletId,
            targetWalletId,
            amount,
            idempotencyKey,
            TransactionType.TRANSFER,
            TransactionStatus.PENDING,
            now,
            null
        );
    }

    public void complete(Instant now) {
        Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (status != TransactionStatus.PENDING) {
            throw new InvalidTransactionStateException();
        }

        status = TransactionStatus.COMPLETED;
        completedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID sourceWalletId() {
        return sourceWalletId;
    }

    public UUID targetWalletId() {
        return targetWalletId;
    }

    public Money amount() {
        return amount;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public TransactionType type() {
        return type;
    }

    public TransactionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
