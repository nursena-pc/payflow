package com.nursena.payflow.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;

public record TopUpWalletResponse(
    UUID id,
    BigDecimal balance,
    Currency currency,
    WalletStatus status,
    Instant createdAt
) {

    public TopUpWalletResponse {
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

    public static TopUpWalletResponse from(
        TopUpWalletResult result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        return new TopUpWalletResponse(
            result.id(),
            result.balance(),
            result.currency(),
            result.status(),
            result.createdAt()
        );
    }
}
