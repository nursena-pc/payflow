package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidCredentialsException
    extends BusinessRuleException {

    public InvalidCredentialsException() {
        super(
            "INVALID_CREDENTIALS",
            "Email or password is incorrect."
        );
    }
}
