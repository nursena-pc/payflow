package com.nursena.payflow.wallet.application.port.in;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.model.Currency;

public record OpenWalletCommand(UUID ownerId, Currency currency) {

    public OpenWalletCommand {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }
}
