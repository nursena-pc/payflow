package com.nursena.payflow.wallet.adapter.in.web;

import com.nursena.payflow.wallet.domain.model.Currency;
import jakarta.validation.constraints.NotNull;

public record OpenWalletRequest(
    @NotNull(message = "Currency is required.")
    Currency currency
) {
}
