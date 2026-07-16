package com.nursena.payflow.transaction.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record GetTransactionHistoryQuery(
    UUID ownerId,
    int page,
    int size
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public GetTransactionHistoryQuery {
        Objects.requireNonNull(
            ownerId,
            "ownerId must not be null"
        );

        if (page < 0) {
            throw new IllegalArgumentException(
                "page must not be negative"
            );
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                "size must be between 1 and "
                    + MAX_SIZE
            );
        }
    }
}
