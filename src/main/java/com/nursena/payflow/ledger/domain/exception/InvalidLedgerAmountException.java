package com.nursena.payflow.ledger.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidLedgerAmountException
    extends BusinessRuleException {

    public InvalidLedgerAmountException() {
        super(
            "INVALID_LEDGER_AMOUNT",
            "Ledger entry amount must be greater than zero."
        );
    }
}
