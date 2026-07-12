package com.nursena.payflow.wallet.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public class InvalidMoneyAmountException extends BusinessRuleException {

    public InvalidMoneyAmountException() {
        super("INVALID_MONEY_AMOUNT", "Money amount must be greater than zero.");
    }
}
