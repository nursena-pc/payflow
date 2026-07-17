package com.nursena.payflow.transaction.application.port.out;

import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;

public interface TransactionHistoryQueryPort {

    TransactionHistoryPage findByWalletId(
        UUID walletId,
        int page,
        int size,
        TransactionHistoryFilter filter
    );
}
