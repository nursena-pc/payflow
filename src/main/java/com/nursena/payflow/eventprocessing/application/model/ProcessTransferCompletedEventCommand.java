package com.nursena.payflow.eventprocessing.application.model;

import java.util.Objects;

import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;

public record ProcessTransferCompletedEventCommand(
    TransferCompletedEvent event,
    String topic,
    int partitionNumber,
    long recordOffset
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    public ProcessTransferCompletedEventCommand {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException(
                "topic must not be blank."
            );
        }

        if (topic.length() > MAX_TOPIC_LENGTH) {
            throw new IllegalArgumentException(
                "topic must not exceed "
                    + MAX_TOPIC_LENGTH
                    + " characters."
            );
        }

        if (partitionNumber < 0) {
            throw new IllegalArgumentException(
                "partitionNumber must not be negative."
            );
        }

        if (recordOffset < 0) {
            throw new IllegalArgumentException(
                "recordOffset must not be negative."
            );
        }
    }
}
