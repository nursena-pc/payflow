package com.nursena.payflow.eventprocessing.adapter.in.web;

final class KafkaDeadLetterOperatorIdentityException
    extends RuntimeException {

    static final String CODE =
        "KAFKA_DEAD_LETTER_OPERATOR_IDENTITY_INVALID";

    KafkaDeadLetterOperatorIdentityException() {
        super(
            "Authenticated operator identity "
                + "is invalid."
        );
    }
}
