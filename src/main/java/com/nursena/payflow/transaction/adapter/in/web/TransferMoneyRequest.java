package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

@Schema(
    name = "TransferMoneyRequest",
    description =
        "Information required to transfer money "
            + "to another wallet."
)
public record TransferMoneyRequest(

    @Schema(
        description = "Identifier of the receiving wallet.",
        example = "f4c8ab12-a4bb-43cb-b6a8-e797cc95e914",
        format = "uuid"
    )
    @NotNull(
        message = "targetWalletId must not be null"
    )
    UUID targetWalletId,

    @Schema(
        description = "Positive amount to transfer.",
        example = "125.50",
        minimum = "0.01"
    )
    @NotNull(message = "amount must not be null")
    @DecimalMin(
        value = "0.01",
        message = "amount must be greater than zero"
    )
    @Digits(
        integer = 17,
        fraction = 2,
        message =
            "amount must have at most 2 fractional digits"
    )
    BigDecimal amount
) {
}
