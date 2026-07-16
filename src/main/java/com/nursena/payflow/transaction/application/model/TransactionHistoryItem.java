package com.nursena.payflow.transaction.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;

public record TransactionHistoryItem(
    UUID transactionId,
    TransactionType type,
    TransactionDirection direction,
    UUID counterpartyWalletId,
    BigDecimal amount,
    Currency currency,
    TransactionStatus status,
    Instant createdAt,
    Instant completedAt
) {

    public TransactionHistoryItem {
        Objects.requireNonNull(
            transactionId,
            "transactionId must not be null"
        );
        Objects.requireNonNull(
            type,
            "type must not be null"
        );
        Objects.requireNonNull(
            direction,
            "direction must not be null"
        );
        Objects.requireNonNull(
            counterpartyWalletId,
            "counterpartyWalletId must not be null"
        );
        Objects.requireNonNull(
            amount,
            "amount must not be null"
        );
        Objects.requireNonNull(
            currency,
            "currency must not be null"
        );
        Objects.requireNonNull(
            status,
            "status must not be null"
        );
        Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                "amount must be greater than zero"
            );
        }
    }
}
