
package com.nursena.payflow.user.application.exception;

import java.time.Duration;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDimension;

public final class LoginRateLimitExceededException
    extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String CODE =
        "LOGIN_RATE_LIMIT_EXCEEDED";

    private static final String MESSAGE =
        "Too many login attempts. Try again later.";

    private final LoginRateLimitDimension
        blockedDimension;

    private final Duration retryAfter;

    public LoginRateLimitExceededException(
        LoginRateLimitDimension blockedDimension,
        Duration retryAfter
    ) {
        super(MESSAGE);

        this.blockedDimension =
            validateBlockedDimension(
                blockedDimension
            );

        this.retryAfter =
            validateRetryAfter(
                retryAfter
            );
    }

    public String getCode() {
        return CODE;
    }

    public LoginRateLimitDimension
    getBlockedDimension() {
        return blockedDimension;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static LoginRateLimitDimension
    validateBlockedDimension(
        LoginRateLimitDimension value
    ) {
        LoginRateLimitDimension validatedValue =
            Objects.requireNonNull(
                value,
                "blockedDimension must not be null"
            );

        if (
            validatedValue
                == LoginRateLimitDimension.NONE
        ) {
            throw new IllegalArgumentException(
                "blockedDimension must not be NONE"
            );
        }

        return validatedValue;
    }

    private static Duration validateRetryAfter(
        Duration value
    ) {
        Duration validatedValue =
            Objects.requireNonNull(
                value,
                "retryAfter must not be null"
            );

        if (
            validatedValue.compareTo(
                Duration.ofSeconds(1)
            ) < 0
        ) {
            throw new IllegalArgumentException(
                "retryAfter must be at least one second"
            );
        }

        return validatedValue;
    }
}
