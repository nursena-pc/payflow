package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class EmailAlreadyRegisteredException extends BusinessRuleException {

    public EmailAlreadyRegisteredException() {
        super(
            "EMAIL_ALREADY_REGISTERED",
            "A user with this email address already exists."
        );
    }
}
