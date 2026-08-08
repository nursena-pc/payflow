package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MfaEnrollmentLifetimePolicyTest {

    @Test
    void shouldCalculateEnrollmentExpiration() {
        Instant now = Instant.parse("2026-08-08T10:00:00Z");
        assertThat(new MfaEnrollmentLifetimePolicy(Duration.ofMinutes(10)).expiresAt(now))
            .isEqualTo(now.plusSeconds(600));
    }

    @Test
    void shouldRejectZeroTtl() {
        assertThatThrownBy(() -> new MfaEnrollmentLifetimePolicy(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeTtl() {
        assertThatThrownBy(() -> new MfaEnrollmentLifetimePolicy(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
