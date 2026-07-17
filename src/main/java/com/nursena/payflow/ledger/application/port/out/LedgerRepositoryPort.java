package com.nursena.payflow.ledger.application.port.out;

import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;

public interface LedgerRepositoryPort {

    DoubleEntryLedger save(DoubleEntryLedger ledger);
}
