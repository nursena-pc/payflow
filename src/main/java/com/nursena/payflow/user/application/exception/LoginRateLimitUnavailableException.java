package com.nursena.payflow.user.application.exception;

public final class LoginRateLimitUnavailableException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String CODE =
        "LOGIN_RATE_LIMIT_UNAVAILABLE";

    private static final String MESSAGE =
        "Login protection is temporarily unavailable.";

    public LoginRateLimitUnavailableException(
        Throwable cause
    ) {
        super(
            MESSAGE,
            cause
        );
    }

    public String getCode() {
        return CODE;
    }
}
