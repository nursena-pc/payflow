package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class IdempotencyConflictException
    extends BusinessRuleException {

    public IdempotencyConflictException() {
        super(
            "IDEMPOTENCY_KEY_CONFLICT",
            "Idempotency key has already been used "
                + "for another transfer request."
        );
    }
}
