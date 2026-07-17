package com.nursena.payflow.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "OpenWalletResponse",
    description = "Wallet created for the authenticated user."
)
public record OpenWalletResponse(

    @Schema(
        description = "Wallet identifier.",
        example = "4a2d98c0-2673-4d21-b5c1-9b69833db721",
        format = "uuid"
    )
    UUID id,

    @Schema(
        description = "Wallet owner identifier.",
        example = "8805681d-d537-42f2-8906-5da1f0666ab7",
        format = "uuid"
    )
    UUID ownerId,

    @Schema(
        description = "Current wallet balance.",
        example = "0.00"
    )
    BigDecimal balance,

    @Schema(
        description = "Wallet currency.",
        example = "TRY"
    )
    Currency currency,

    @Schema(
        description = "Current wallet status.",
        example = "ACTIVE"
    )
    WalletStatus status,

    @Schema(
        description = "Wallet creation time.",
        example = "2026-07-17T12:00:00Z",
        format = "date-time"
    )
    Instant createdAt
) {

    public OpenWalletResponse {
        Objects.requireNonNull(
            id,
            "id must not be null"
        );
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
