package com.nursena.payflow.transaction.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferMoneyCommand(
    UUID ownerId,
    UUID targetWalletId,
    BigDecimal amount,
    String idempotencyKey
) {

    public TransferMoneyCommand {
        Objects.requireNonNull(
            ownerId,
            "ownerId must not be null"
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
    }
}
