package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidRefreshTokenException
    extends BusinessRuleException {

    public InvalidRefreshTokenException() {
        super(
            "REFRESH_TOKEN_INVALID",
            "Refresh token is invalid."
        );
    }
}
