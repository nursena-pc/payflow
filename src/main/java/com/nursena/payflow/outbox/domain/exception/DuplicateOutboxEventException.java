package com.nursena.payflow.outbox.domain.exception;

public class DuplicateOutboxEventException
    extends RuntimeException {

    public DuplicateOutboxEventException() {
        super(
            "An outbox event already exists "
                + "for the same deduplication key."
        );
    }
}
