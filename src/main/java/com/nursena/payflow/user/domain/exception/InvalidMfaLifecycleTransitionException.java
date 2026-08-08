package com.nursena.payflow.user.domain.exception;

public final class InvalidMfaLifecycleTransitionException
    extends RuntimeException {

    private static final String MESSAGE =
        "MFA state transition is invalid.";

    public InvalidMfaLifecycleTransitionException() {
        super(MESSAGE);
    }
}
