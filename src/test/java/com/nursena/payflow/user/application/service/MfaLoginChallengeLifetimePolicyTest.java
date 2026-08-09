package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MfaLoginChallengeLifetimePolicyTest {

    @Test
    void shouldCalculateExpiration() {
        Instant issued = Instant.parse("2026-08-08T12:00:00Z");
        MfaLoginChallengeLifetimePolicy policy =
            new MfaLoginChallengeLifetimePolicy(Duration.ofMinutes(5), 5);
        assertThat(policy.expiresAt(issued)).isEqualTo(issued.plusSeconds(300));
    }

    @Test
    void shouldExposeAttemptLimit() {
        assertThat(new MfaLoginChallengeLifetimePolicy(Duration.ofMinutes(5), 4).maxAttempts())
            .isEqualTo(4);
    }

    @Test
    void shouldRejectZeroTtl() {
        assertThatThrownBy(() -> new MfaLoginChallengeLifetimePolicy(Duration.ZERO, 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroAttempts() {
        assertThatThrownBy(() -> new MfaLoginChallengeLifetimePolicy(Duration.ofMinutes(5), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
