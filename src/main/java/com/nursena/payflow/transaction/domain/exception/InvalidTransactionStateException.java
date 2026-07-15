package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidTransactionStateException
    extends BusinessRuleException {

    public InvalidTransactionStateException() {
        super(
            "INVALID_TRANSACTION_STATE",
            "Transaction state does not allow this operation."
        );
    }
}
