package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class WalletConcurrentUpdateException
    extends BusinessRuleException {

    public WalletConcurrentUpdateException() {
        super(
            "WALLET_CONCURRENT_UPDATE",
            "Wallet was updated concurrently. Please retry the operation."
        );
    }
}
