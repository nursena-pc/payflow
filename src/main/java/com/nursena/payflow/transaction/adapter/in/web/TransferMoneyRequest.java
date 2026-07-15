package com.nursena.payflow.transaction.adapter.in.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record TransferMoneyRequest(

    @NotNull(message = "targetWalletId must not be null")
    UUID targetWalletId,

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
