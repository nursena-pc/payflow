package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class MfaVerificationFailedException
    extends BusinessRuleException {

    public MfaVerificationFailedException() {
        super(
            "MFA_VERIFICATION_FAILED",
            "The MFA proof could not be verified."
        );
    }
}
