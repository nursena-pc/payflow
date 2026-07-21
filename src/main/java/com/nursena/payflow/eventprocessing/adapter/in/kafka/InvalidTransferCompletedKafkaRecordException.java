package com.nursena.payflow.eventprocessing.adapter.in.kafka;

public final class
InvalidTransferCompletedKafkaRecordException
    extends RuntimeException {

    InvalidTransferCompletedKafkaRecordException(
        String message
    ) {
        super(message);
    }
}
