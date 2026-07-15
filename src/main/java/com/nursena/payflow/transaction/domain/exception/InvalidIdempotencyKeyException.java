package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidIdempotencyKeyException
    extends BusinessRuleException {

    public InvalidIdempotencyKeyException() {
        super(
            "INVALID_IDEMPOTENCY_KEY",
            "Idempotency key must contain between 1 and 100 characters."
        );
    }
}
