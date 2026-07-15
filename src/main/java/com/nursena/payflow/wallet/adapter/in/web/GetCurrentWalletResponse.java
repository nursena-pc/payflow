package com.nursena.payflow.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;

public record GetCurrentWalletResponse(
    UUID id,
    BigDecimal balance,
    Currency currency,
    WalletStatus status,
    Instant createdAt
) {

    public GetCurrentWalletResponse {
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

    public static GetCurrentWalletResponse from(
        GetCurrentWalletResult result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        return new GetCurrentWalletResponse(
            result.id(),
            result.balance(),
            result.currency(),
            result.status(),
            result.createdAt()
        );
    }
}
