package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MfaLoginChallengePropertiesTest {

    @Test
    void shouldAcceptBoundedDefaults() {
        MfaLoginChallengeProperties properties =
            new MfaLoginChallengeProperties(Duration.ofMinutes(5), 5);
        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxAttempts()).isEqualTo(5);
    }

    @Test
    void shouldRejectLongTtl() {
        assertThatThrownBy(() -> new MfaLoginChallengeProperties(Duration.ofMinutes(16), 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroTtl() {
        assertThatThrownBy(() -> new MfaLoginChallengeProperties(Duration.ZERO, 5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectTooManyAttempts() {
        assertThatThrownBy(() -> new MfaLoginChallengeProperties(Duration.ofMinutes(5), 11))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
