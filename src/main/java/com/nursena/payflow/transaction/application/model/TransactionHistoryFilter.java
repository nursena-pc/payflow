package com.nursena.payflow.transaction.application.model;

import java.time.Instant;

import com.nursena.payflow.transaction.domain.model.TransactionStatus;

public record TransactionHistoryFilter(
    TransactionDirection direction,
    TransactionStatus status,
    Instant from,
    Instant to
) {

    public TransactionHistoryFilter {
        if (
            from != null
                && to != null
                && from.isAfter(to)
        ) {
            throw new IllegalArgumentException(
                "from must not be after to"
            );
        }
    }

    public static TransactionHistoryFilter unfiltered() {
        return new TransactionHistoryFilter(
            null,
            null,
            null,
            null
        );
    }
}
