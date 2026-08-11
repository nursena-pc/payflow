package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidStepUpGrantException
    extends BusinessRuleException {

    public InvalidStepUpGrantException() {
        super(
            "STEP_UP_INVALID",
            "The supplied step-up proof cannot authorize the requested operation."
        );
    }
}
