package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.wallet.domain.model.Currency;

public record TransferMoneyResponse(
    UUID transactionId,
    UUID sourceWalletId,
    UUID targetWalletId,
    BigDecimal amount,
    Currency currency,
    TransactionStatus status,
    Instant createdAt,
    Instant completedAt
) {

    public TransferMoneyResponse {
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
        Objects.requireNonNull(
            completedAt,
            "completedAt must not be null"
        );
    }

    public static TransferMoneyResponse from(
        TransferMoneyResult result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        return new TransferMoneyResponse(
            result.transactionId(),
            result.sourceWalletId(),
            result.targetWalletId(),
            result.amount(),
            result.currency(),
            result.status(),
            result.createdAt(),
            result.completedAt()
        );
    }
}
