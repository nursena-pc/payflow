package com.nursena.payflow.wallet.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Wallet;
import com.nursena.payflow.wallet.domain.model.WalletStatus;

public record TopUpWalletResult(
    UUID id,
    BigDecimal balance,
    Currency currency,
    WalletStatus status,
    Instant createdAt
) {

    public TopUpWalletResult {
        Objects.requireNonNull(
            id,
            "id must not be null"
        );
        Objects.requireNonNull(
            balance,
            "balance must not be null"
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
    }

    public static TopUpWalletResult from(
        Wallet wallet
    ) {
        Objects.requireNonNull(
            wallet,
            "wallet must not be null"
        );

        return new TopUpWalletResult(
            wallet.id(),
            wallet.balance().amount(),
            wallet.balance().currency(),
            wallet.status(),
            wallet.createdAt()
        );
    }
}
