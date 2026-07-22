package com.nursena.payflow.eventprocessing.adapter.in.kafka;

public final class
TransferCompletedEventDeserializationException
    extends RuntimeException {

    TransferCompletedEventDeserializationException(
        String topic,
        int partition,
        long offset,
        Throwable cause
    ) {
        super(
            "Could not deserialize transfer completed "
                + "event from topic "
                + topic
                + ", partition "
                + partition
                + ", offset "
                + offset
                + ".",
            cause
        );
    }
}
