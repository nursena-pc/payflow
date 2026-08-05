package com.nursena.payflow.maildelivery.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class MailOutboxPollingPropertiesTest {

    @Test
    void shouldAcceptBoundedPollingConfiguration() {
        MailOutboxPollingProperties properties =
            new MailOutboxPollingProperties(
                true,
                "mail-worker",
                50,
                Duration.ofSeconds(30),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
            );

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.batchSize()).isEqualTo(50);
    }

    @Test
    void shouldRejectNonPositiveLease() {
        assertThatThrownBy(() -> new MailOutboxPollingProperties(
            true,
            "mail-worker",
            50,
            Duration.ZERO,
            Duration.ofSeconds(2),
            Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
