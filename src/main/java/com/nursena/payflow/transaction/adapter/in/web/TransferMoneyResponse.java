package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.wallet.domain.model.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "TransferMoneyResponse",
    description = "Completed wallet transfer information."
)
public record TransferMoneyResponse(

    @Schema(
        description = "Payment transaction identifier.",
        example = "aa3ab0b3-3d85-42d7-a641-2ec43cd81dd9",
        format = "uuid"
    )
    UUID transactionId,

    @Schema(
        description = "Wallet debited by the transfer.",
        example = "4a2d98c0-2673-4d21-b5c1-9b69833db721",
        format = "uuid"
    )
    UUID sourceWalletId,

    @Schema(
        description = "Wallet credited by the transfer.",
        example = "f4c8ab12-a4bb-43cb-b6a8-e797cc95e914",
        format = "uuid"
    )
    UUID targetWalletId,

    @Schema(
        description = "Transferred amount.",
        example = "125.50"
    )
    BigDecimal amount,

    @Schema(
        description = "Transfer currency.",
        example = "TRY"
    )
    Currency currency,

    @Schema(
        description = "Current transaction status.",
        example = "COMPLETED"
    )
    TransactionStatus status,

    @Schema(
        description = "Transaction creation time.",
        example = "2026-07-17T12:00:00Z",
        format = "date-time"
    )
    Instant createdAt,

    @Schema(
        description = "Transaction completion time.",
        example = "2026-07-17T12:00:00.125Z",
        format = "date-time"
    )
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
