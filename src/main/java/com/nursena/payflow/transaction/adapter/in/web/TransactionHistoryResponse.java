package com.nursena.payflow.transaction.adapter.in.web;

import java.util.List;
import java.util.Objects;

import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;

public record TransactionHistoryResponse(
    List<TransactionHistoryItemResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean hasNext,
    boolean hasPrevious
) {

    static TransactionHistoryResponse from(
        TransactionHistoryPage result
    ) {
        Objects.requireNonNull(
            result,
            "result must not be null"
        );

        List<TransactionHistoryItemResponse> items =
            result.items()
                .stream()
                .map(
                    TransactionHistoryItemResponse::from
                )
                .toList();

        return new TransactionHistoryResponse(
            items,
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.first(),
            result.last(),
            result.hasNext(),
            result.hasPrevious()
        );
    }
}
