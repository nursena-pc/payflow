package com.nursena.payflow.eventprocessing.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterReplayPropertiesTest {

    private static final String WORKER_ID =
        "replay-worker-1";

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration SEND_TIMEOUT =
        Duration.ofSeconds(10);

    @Test
    void shouldCreateValidProperties() {
        KafkaDeadLetterReplayProperties properties =
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                LEASE_DURATION,
                MAX_ATTEMPTS,
                SEND_TIMEOUT
            );

        assertThat(properties.workerId())
            .isEqualTo(WORKER_ID);

        assertThat(properties.leaseDuration())
            .isEqualTo(LEASE_DURATION);

        assertThat(properties.maxAttempts())
            .isEqualTo(MAX_ATTEMPTS);

        assertThat(properties.sendTimeout())
            .isEqualTo(SEND_TIMEOUT);
    }

    @Test
    void shouldRejectInvalidWorkerId() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                " ",
                LEASE_DURATION,
                MAX_ATTEMPTS,
                SEND_TIMEOUT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "workerId must not be blank."
            );
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                Duration.ZERO,
                MAX_ATTEMPTS,
                SEND_TIMEOUT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "leaseDuration must be positive."
            );
    }

    @Test
    void shouldRejectInvalidMaximumAttempts() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                LEASE_DURATION,
                0,
                SEND_TIMEOUT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "maxAttempts must be positive."
            );
    }

    @Test
    void shouldValidateSendTimeout() {
        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                LEASE_DURATION,
                MAX_ATTEMPTS,
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "sendTimeout must not be null"
            );

        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                LEASE_DURATION,
                MAX_ATTEMPTS,
                Duration.ZERO
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "sendTimeout must be positive."
            );

        assertThatThrownBy(() ->
            new KafkaDeadLetterReplayProperties(
                WORKER_ID,
                LEASE_DURATION,
                MAX_ATTEMPTS,
                Duration.ofNanos(999_999)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "sendTimeout must be at least "
                    + "one millisecond."
            );
    }
}
