package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class WalletNotFoundException
    extends BusinessRuleException {

    public WalletNotFoundException() {
        super(
            "WALLET_NOT_FOUND",
            "Wallet could not be found."
        );
    }
}
