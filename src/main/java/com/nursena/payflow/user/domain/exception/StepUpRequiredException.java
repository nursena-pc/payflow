package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class StepUpRequiredException
    extends BusinessRuleException {

    public StepUpRequiredException() {
        super(
            "STEP_UP_REQUIRED",
            "The authenticated operation requires a recent purpose-bound second-factor proof."
        );
    }
}
