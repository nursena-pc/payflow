package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public class WalletNotActiveException extends BusinessRuleException {

    public WalletNotActiveException() {
        super("WALLET_NOT_ACTIVE", "Wallet must be active to perform this operation.");
    }
}
