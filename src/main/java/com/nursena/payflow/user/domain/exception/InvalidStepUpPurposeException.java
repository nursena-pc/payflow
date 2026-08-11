package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidStepUpPurposeException
    extends BusinessRuleException {

    public InvalidStepUpPurposeException() {
        super(
            "VALIDATION_FAILED",
            "The step-up purpose is invalid."
        );
    }
}
