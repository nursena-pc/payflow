package com.nursena.payflow.wallet.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "TopUpWalletRequest",
    description = "Amount credited to the current wallet."
)
public record TopUpWalletRequest(

    @Schema(
        description = "Positive top-up amount.",
        example = "100.00",
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
        message = "amount must have at most 2 fractional digits"
    )
    BigDecimal amount
) {
}
