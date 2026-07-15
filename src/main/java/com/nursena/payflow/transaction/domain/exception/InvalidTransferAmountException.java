package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidTransferAmountException
    extends BusinessRuleException {

    public InvalidTransferAmountException() {
        super(
            "INVALID_TRANSFER_AMOUNT",
            "Transfer amount must be greater than zero."
        );
    }
}
