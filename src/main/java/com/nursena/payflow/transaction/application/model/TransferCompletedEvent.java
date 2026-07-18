package com.nursena.payflow.transaction.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;

public record TransferCompletedEvent(
    UUID eventId,
    String eventType,
    int eventVersion,
    Instant occurredAt,
    UUID transactionId,
    UUID sourceWalletId,
    UUID targetWalletId,
    String amount,
    String currency
) {

    public static final String TYPE =
        "wallet.transfer.completed";

    public static final int VERSION = 1;

    public TransferCompletedEvent {
        Objects.requireNonNull(
            eventId,
            "eventId must not be null"
        );

        Objects.requireNonNull(
            occurredAt,
            "occurredAt must not be null"
        );

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

        if (!TYPE.equals(eventType)) {
            throw new IllegalArgumentException(
                "eventType must be "
                    + TYPE
                    + "."
            );
        }

        if (eventVersion != VERSION) {
            throw new IllegalArgumentException(
                "eventVersion must be "
                    + VERSION
                    + "."
            );
        }

        if (amount == null || amount.isBlank()) {
            throw new IllegalArgumentException(
                "amount must not be blank."
            );
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                "currency must not be blank."
            );
        }
    }

    public static TransferCompletedEvent from(
        PaymentTransaction transaction
    ) {
        Objects.requireNonNull(
            transaction,
            "transaction must not be null"
        );

        if (transaction.status()
            != TransactionStatus.COMPLETED) {

            throw new IllegalArgumentException(
                "transaction must be completed."
            );
        }

        if (transaction.completedAt() == null) {
            throw new IllegalArgumentException(
                "completed transaction must have completedAt."
            );
        }

        return new TransferCompletedEvent(
            UUID.randomUUID(),
            TYPE,
            VERSION,
            transaction.completedAt(),
            transaction.id(),
            transaction.sourceWalletId(),
            transaction.targetWalletId(),
            transaction
                .amount()
                .amount()
                .toPlainString(),
            transaction
                .amount()
                .currency()
                .name()
        );
    }
}
