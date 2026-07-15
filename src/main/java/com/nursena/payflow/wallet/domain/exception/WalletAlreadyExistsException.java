package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class WalletAlreadyExistsException
    extends BusinessRuleException {

    public WalletAlreadyExistsException() {
        super(
            "WALLET_ALREADY_EXISTS",
            "User already has a wallet."
        );
    }
}
