package com.nursena.payflow.user.application.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class MfaSecurityUnavailableException
    extends BusinessRuleException {

    public MfaSecurityUnavailableException() {
        super(
            "MFA_SECURITY_UNAVAILABLE",
            "MFA security processing is temporarily unavailable."
        );
    }
}
