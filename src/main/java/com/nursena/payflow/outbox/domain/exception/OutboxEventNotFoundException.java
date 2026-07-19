package com.nursena.payflow.outbox.domain.exception;

import java.util.UUID;

public final class OutboxEventNotFoundException
    extends RuntimeException {

    public OutboxEventNotFoundException(
        UUID eventId
    ) {
        super(
            "Outbox event not found: "
                + eventId
        );
    }
}
