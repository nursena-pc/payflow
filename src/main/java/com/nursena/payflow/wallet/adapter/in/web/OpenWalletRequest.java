package com.nursena.payflow.wallet.adapter.in.web;

import com.nursena.payflow.wallet.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "OpenWalletRequest",
    description = "Information required to open a wallet."
)
public record OpenWalletRequest(

    @Schema(
        description = "Wallet currency.",
        example = "TRY"
    )
    @NotNull(message = "Currency is required.")
    Currency currency
) {
}
