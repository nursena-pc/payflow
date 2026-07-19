package com.nursena.payflow.outbox.domain.exception;

public class InvalidOutboxEventException
    extends RuntimeException {

    public InvalidOutboxEventException(
        String message
    ) {
        super(message);
    }
}
