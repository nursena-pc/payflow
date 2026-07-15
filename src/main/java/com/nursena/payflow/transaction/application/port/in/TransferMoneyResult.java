package com.nursena.payflow.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.wallet.domain.model.Currency;

public record TransferMoneyResult(
    UUID transactionId,
    UUID sourceWalletId,
    UUID targetWalletId,
    BigDecimal amount,
    Currency currency,
    TransactionStatus status,
    Instant createdAt,
    Instant completedAt
) {

    public static TransferMoneyResult from(
        PaymentTransaction transaction
    ) {
        return new TransferMoneyResult(
            transaction.id(),
            transaction.sourceWalletId(),
            transaction.targetWalletId(),
            transaction.amount().amount(),
            transaction.amount().currency(),
            transaction.status(),
            transaction.createdAt(),
            transaction.completedAt()
        );
    }
}
