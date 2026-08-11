package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StepUpGrantLifetimePolicyTest {

    @Test
    void shouldCalculateExpirationFromInjectedLifetime() {
        Instant issued = Instant.parse("2026-08-10T10:00:00Z");
        assertThat(new StepUpGrantLifetimePolicy(Duration.ofMinutes(5)).expiresAt(issued))
            .isEqualTo(issued.plusSeconds(300));
    }

    @Test
    void shouldRejectNonPositiveLifetime() {
        assertThatThrownBy(() -> new StepUpGrantLifetimePolicy(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
