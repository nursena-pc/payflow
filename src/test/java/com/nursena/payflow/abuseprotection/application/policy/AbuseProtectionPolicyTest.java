package com.nursena.payflow.abuseprotection.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AbuseProtectionPolicyTest {

    @Test
    void shouldRetainValidatedPolicy() {
        AbuseProtectionPolicy policy =
            new AbuseProtectionPolicy(
                true,
                Duration.ofMinutes(15),
                5,
                20,
                AbuseProtectionFailureMode.FAIL_CLOSED
            );

        assertThat(policy.enabled()).isTrue();
        assertThat(policy.window())
            .isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.identityLimit()).isEqualTo(5);
        assertThat(policy.clientLimit()).isEqualTo(20);
        assertThat(policy.dependencyFailureMode())
            .isEqualTo(
                AbuseProtectionFailureMode.FAIL_CLOSED
            );
    }

    @Test
    void shouldRejectSubSecondWindow() {
        assertThatThrownBy(() ->
            policy(
                Duration.ofMillis(999),
                5,
                20
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "window must be at least one second"
            );
    }

    @Test
    void shouldRejectWindowLongerThanOneDay() {
        assertThatThrownBy(() ->
            policy(
                Duration.ofDays(1).plusSeconds(1),
                5,
                20
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "window must not exceed one day"
            );
    }

    @Test
    void shouldRejectUnboundedLimit() {
        assertThatThrownBy(() ->
            policy(
                Duration.ofMinutes(15),
                1_000_001,
                20
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(
                "identityLimit must not exceed 1000000"
            );
    }

    private static AbuseProtectionPolicy policy(
        Duration window,
        int identityLimit,
        int clientLimit
    ) {
        return new AbuseProtectionPolicy(
            true,
            window,
            identityLimit,
            clientLimit,
            AbuseProtectionFailureMode.FAIL_CLOSED
        );
    }
}
