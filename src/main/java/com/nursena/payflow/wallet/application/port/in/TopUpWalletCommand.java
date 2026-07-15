package com.nursena.payflow.wallet.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TopUpWalletCommand(
    UUID ownerId,
    BigDecimal amount
) {

    public TopUpWalletCommand {
        Objects.requireNonNull(
            ownerId,
            "ownerId must not be null"
        );
        Objects.requireNonNull(
            amount,
            "amount must not be null"
        );
    }
}
