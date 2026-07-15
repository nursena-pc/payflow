package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidEmailException extends BusinessRuleException {

    public InvalidEmailException() {
        super("INVALID_EMAIL", "Email address is invalid.");
    }
}
