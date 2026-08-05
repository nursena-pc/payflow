package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception
    .BusinessRuleException;

public final class InvalidAccountActionCredentialException
    extends BusinessRuleException {

    public InvalidAccountActionCredentialException() {
        super(
            "ACCOUNT_ACTION_CREDENTIAL_INVALID",
            "Account action credential is invalid."
        );
    }
}
