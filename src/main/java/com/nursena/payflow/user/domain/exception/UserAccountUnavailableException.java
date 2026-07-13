package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class UserAccountUnavailableException
    extends BusinessRuleException {

    public UserAccountUnavailableException() {
        super(
            "USER_ACCOUNT_UNAVAILABLE",
            "User account is not available for authentication."
        );
    }
}
