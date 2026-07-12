package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public class InsufficientBalanceException extends BusinessRuleException {

    public InsufficientBalanceException() {
        super("INSUFFICIENT_BALANCE", "Wallet balance is insufficient for this operation.");
    }
}
