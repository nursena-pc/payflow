package com.nursena.payflow.transaction.adapter.out.outbox;

public final class TransferCompletedEventSerializationException
    extends RuntimeException {

    public TransferCompletedEventSerializationException(
        Throwable cause
    ) {
        super(
            "Transfer completed event could not be serialized.",
            cause
        );
    }
}
