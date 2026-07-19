package com.nursena.payflow.outbox.domain.exception;

public class InvalidOutboxEventStateException
    extends RuntimeException {

    public InvalidOutboxEventStateException(
        String message
    ) {
        super(message);
    }
}
