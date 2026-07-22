package com.nursena.payflow.eventprocessing.application.model;

import java.util.List;
import java.util.Objects;

public record KafkaDeadLetterRecordPage(
    List<KafkaDeadLetterRecordSummary> items,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public KafkaDeadLetterRecordPage {
        items =
            List.copyOf(
                Objects.requireNonNull(
                    items,
                    "items must not be null"
                )
            );

        if (page < 0) {
            throw new IllegalArgumentException(
                "page must not be negative"
            );
        }

        if (size < 1) {
            throw new IllegalArgumentException(
                "size must be greater than zero"
            );
        }

        if (totalElements < 0) {
            throw new IllegalArgumentException(
                "totalElements must not be negative"
            );
        }

        if (totalPages < 0) {
            throw new IllegalArgumentException(
                "totalPages must not be negative"
            );
        }
    }

    public boolean first() {
        return page == 0;
    }

    public boolean last() {
        return totalPages == 0
            || page >= totalPages - 1;
    }

    public boolean hasNext() {
        return !last();
    }

    public boolean hasPrevious() {
        return page > 0;
    }
}
