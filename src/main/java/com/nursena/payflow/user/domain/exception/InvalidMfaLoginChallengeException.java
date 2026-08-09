package com.nursena.payflow.user.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class InvalidMfaLoginChallengeException
    extends BusinessRuleException {

    public InvalidMfaLoginChallengeException() {
        super(
            "MFA_CHALLENGE_INVALID",
            "The MFA challenge or proof could not be verified."
        );
    }
}
