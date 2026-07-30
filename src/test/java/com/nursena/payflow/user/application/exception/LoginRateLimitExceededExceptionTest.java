
package com.nursena.payflow.user.application.exception;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.time.Duration;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDimension;
import org.junit.jupiter.api.Test;

class LoginRateLimitExceededExceptionTest {

    @Test
    void shouldExposeSafeRateLimitState() {
        LoginRateLimitExceededException exception =
            new LoginRateLimitExceededException(
                LoginRateLimitDimension.CLIENT,
                Duration.ofSeconds(45)
            );

        assertThat(exception.getCode())
            .isEqualTo(
                "LOGIN_RATE_LIMIT_EXCEEDED"
            );

        assertThat(exception.getMessage())
            .isEqualTo(
                "Too many login attempts. "
                    + "Try again later."
            );

        assertThat(exception.getBlockedDimension())
            .isEqualTo(
                LoginRateLimitDimension.CLIENT
            );

        assertThat(exception.getRetryAfter())
            .isEqualTo(
                Duration.ofSeconds(45)
            );
    }

    @Test
    void shouldRejectNoneDimension() {
        assertThatThrownBy(() ->
            new LoginRateLimitExceededException(
                LoginRateLimitDimension.NONE,
                Duration.ofSeconds(1)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "blockedDimension must not be NONE"
            );
    }
}
