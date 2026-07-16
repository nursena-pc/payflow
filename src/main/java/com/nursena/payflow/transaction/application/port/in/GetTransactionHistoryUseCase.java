package com.nursena.payflow.transaction.application.port.in;

import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;

public interface GetTransactionHistoryUseCase {

    TransactionHistoryPage getTransactionHistory(
        GetTransactionHistoryQuery query
    );
}
