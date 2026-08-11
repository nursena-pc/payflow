package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StepUpGrantPropertiesTest {

    @Test
    void shouldAcceptPositiveLifetimeUpToFifteenMinutes() {
        assertThat(new StepUpGrantProperties(Duration.ofMinutes(5)).ttl())
            .isEqualTo(Duration.ofMinutes(5));
        assertThat(new StepUpGrantProperties(Duration.ofMinutes(15)).ttl())
            .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void shouldRejectZeroNegativeOrExcessiveLifetime() {
        assertThatThrownBy(() -> new StepUpGrantProperties(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StepUpGrantProperties(Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StepUpGrantProperties(Duration.ofMinutes(16)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
