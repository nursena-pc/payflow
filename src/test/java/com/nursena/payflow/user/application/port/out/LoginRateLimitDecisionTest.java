package com.nursena.payflow.user.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class LoginRateLimitDecisionTest {

    @Test
    void shouldCreateAllowedDecision() {
        LoginRateLimitDecision decision =
            LoginRateLimitDecision.allowed();

        assertThat(decision.isAllowed())
            .isTrue();

        assertThat(decision.blockedDimension())
            .isEqualTo(
                LoginRateLimitDimension.NONE
            );

        assertThat(decision.retryAfter())
            .isZero();
    }

    @Test
    void shouldCreateBlockedDecision() {
        LoginRateLimitDecision decision =
            LoginRateLimitDecision.blocked(
                LoginRateLimitDimension.IDENTITY,
                Duration.ofMinutes(3)
            );

        assertThat(decision.isAllowed())
            .isFalse();

        assertThat(decision.blockedDimension())
            .isEqualTo(
                LoginRateLimitDimension.IDENTITY
            );

        assertThat(decision.retryAfter())
            .isEqualTo(
                Duration.ofMinutes(3)
            );
    }

    @Test
    void shouldRejectNoneAsBlockedDimension() {
        assertThatThrownBy(() ->
            LoginRateLimitDecision.blocked(
                LoginRateLimitDimension.NONE,
                Duration.ofSeconds(1)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "blocked dimension must not be NONE"
            );
    }

    @Test
    void shouldRejectNonPositiveBlockedRetryDelay() {
        assertThatThrownBy(() ->
            LoginRateLimitDecision.blocked(
                LoginRateLimitDimension.CLIENT,
                Duration.ZERO
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "blocked decision retryAfter "
                    + "must be positive"
            );
    }
}
