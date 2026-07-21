package com.nursena.payflow.eventprocessing.adapter.in.kafka;

class InvalidKafkaDeadLetterRecordException
    extends RuntimeException {

    InvalidKafkaDeadLetterRecordException(
        String message
    ) {
        super(message);
    }
}
