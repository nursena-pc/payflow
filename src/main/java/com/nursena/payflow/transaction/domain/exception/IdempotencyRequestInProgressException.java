package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class IdempotencyRequestInProgressException
    extends BusinessRuleException {

    public IdempotencyRequestInProgressException() {
        super(
            "IDEMPOTENCY_REQUEST_IN_PROGRESS",
            "A transfer with this idempotency key "
                + "is still being processed."
        );
    }
}
