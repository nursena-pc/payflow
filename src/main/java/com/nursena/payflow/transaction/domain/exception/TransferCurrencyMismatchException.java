package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class TransferCurrencyMismatchException
    extends BusinessRuleException {

    public TransferCurrencyMismatchException() {
        super(
            "TRANSFER_CURRENCY_MISMATCH",
            "Source and target wallet currencies must match."
        );
    }
}
