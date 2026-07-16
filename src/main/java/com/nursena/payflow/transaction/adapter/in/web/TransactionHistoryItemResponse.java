package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryItem;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;

public record TransactionHistoryItemResponse(
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

    static TransactionHistoryItemResponse from(
        TransactionHistoryItem item
    ) {
        Objects.requireNonNull(
            item,
            "item must not be null"
        );

        return new TransactionHistoryItemResponse(
            item.transactionId(),
            item.type(),
            item.direction(),
            item.counterpartyWalletId(),
            item.amount(),
            item.currency(),
            item.status(),
            item.createdAt(),
            item.completedAt()
        );
    }
}
