package com.nursena.payflow.abuseprotection.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AbuseProtectionDecisionTest {

    @Test
    void shouldCreateAllowedDecision() {
        AbuseProtectionDecision decision =
            AbuseProtectionDecision.allowed();

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.blockedDimension())
            .isEqualTo(AbuseProtectionDimension.NONE);
        assertThat(decision.retryAfter()).isZero();
    }

    @Test
    void shouldCreateBlockedDecision() {
        AbuseProtectionDecision decision =
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.BOTH,
                Duration.ofSeconds(30)
            );

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.retryAfter())
            .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldRejectInvalidDecisionShapes() {
        assertThatThrownBy(() ->
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.NONE,
                Duration.ofSeconds(1)
            )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.IDENTITY,
                Duration.ZERO
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
