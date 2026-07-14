package com.nursena.payflow.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;

public record OpenWalletResponse(
    UUID id,
    UUID ownerId,
    BigDecimal balance,
    Currency currency,
    WalletStatus status,
    Instant createdAt
) {

    public OpenWalletResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(
            ownerId,
            "ownerId must not be null"
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

    public static OpenWalletResponse from(
        OpenWalletResult result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        return new OpenWalletResponse(
            result.id(),
            result.ownerId(),
            result.balance(),
            result.currency(),
            result.status(),
            result.createdAt()
        );
    }
}
