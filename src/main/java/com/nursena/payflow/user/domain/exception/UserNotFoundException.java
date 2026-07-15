package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class UserNotFoundException
    extends BusinessRuleException {

    public UserNotFoundException() {
        super(
            "USER_NOT_FOUND",
            "User could not be found."
        );
    }
}
