package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class MfaStateConflictException
    extends BusinessRuleException {

    public MfaStateConflictException() {
        super(
            "MFA_STATE_CONFLICT",
            "The MFA operation cannot be applied in the current state."
        );
    }
}
