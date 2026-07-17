package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryItem;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "TransactionHistoryItemResponse",
    description =
        "A transaction from the authenticated user's perspective."
)
public record TransactionHistoryItemResponse(

    @Schema(
        description = "Payment transaction identifier.",
        example =
            "aa3ab0b3-3d85-42d7-a641-2ec43cd81dd9",
        format = "uuid"
    )
    UUID transactionId,

    @Schema(
        description = "Transaction type.",
        example = "TRANSFER"
    )
    TransactionType type,

    @Schema(
        description =
            "Transaction direction relative to "
                + "the authenticated user's wallet.",
        example = "OUTGOING"
    )
    TransactionDirection direction,

    @Schema(
        description =
            "Wallet on the opposite side of the transaction.",
        example =
            "f4c8ab12-a4bb-43cb-b6a8-e797cc95e914",
        format = "uuid"
    )
    UUID counterpartyWalletId,

    @Schema(
        description = "Transaction amount.",
        example = "125.50"
    )
    BigDecimal amount,

    @Schema(
        description = "Transaction currency.",
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
        description =
            "Transaction completion time. It may be absent "
                + "until processing has completed.",
        example = "2026-07-17T12:00:00.125Z",
        format = "date-time"
    )
    Instant completedAt
) {

    static TransactionHistoryItemResponse from(
        TransactionHistoryItem item
    ) {
        Objects.requireNonNull(
            item,
            "item must not be null"
        );

        return new TransactionHistoryItemResponse(
            item.transactionId(),
            item.type(),
            item.direction(),
            item.counterpartyWalletId(),
            item.amount(),
            item.currency(),
            item.status(),
            item.createdAt(),
            item.completedAt()
        );
    }
}
