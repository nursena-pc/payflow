package com.nursena.payflow.ledger.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidLedgerEntryPairException
    extends BusinessRuleException {

    public InvalidLedgerEntryPairException() {
        super(
            "INVALID_LEDGER_ENTRY_PAIR",
            "Ledger entries must form a balanced debit and credit pair."
        );
    }
}
