package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class PaymentTransactionNotFoundException
    extends BusinessRuleException {

    public PaymentTransactionNotFoundException() {
        super(
            "PAYMENT_TRANSACTION_NOT_FOUND",
            "Payment transaction could not be found."
        );
    }
}
