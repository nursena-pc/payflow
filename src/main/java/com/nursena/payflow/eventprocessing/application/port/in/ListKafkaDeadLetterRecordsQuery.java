package com.nursena.payflow.eventprocessing.application.port.in;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;

public record ListKafkaDeadLetterRecordsQuery(
    int page,
    int size,
    KafkaDeadLetterRecordFilter filter
) {

    public static final int MAX_SIZE = 100;

    public ListKafkaDeadLetterRecordsQuery {
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

        if (size > MAX_SIZE) {
            throw new IllegalArgumentException(
                "size must not exceed "
                    + MAX_SIZE
            );
        }

        filter =
            Objects.requireNonNull(
                filter,
                "filter must not be null"
            );
    }
}
