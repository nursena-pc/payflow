package com.nursena.payflow.outbox.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PublishOutboxEventsCommandTest {

    @Test
    void shouldCreateValidCommand() {
        PublishOutboxEventsCommand command =
            new PublishOutboxEventsCommand(
                "publisher-1",
                25,
                Duration.ofSeconds(30)
            );

        assertThat(command.publisherId())
            .isEqualTo("publisher-1");

        assertThat(command.batchSize())
            .isEqualTo(25);

        assertThat(command.leaseDuration())
            .isEqualTo(
                Duration.ofSeconds(30)
            );
    }

    @Test
    void shouldRejectBlankPublisherId() {
        assertThatThrownBy(() ->
            new PublishOutboxEventsCommand(
                " ",
                25,
                Duration.ofSeconds(30)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "publisherId must not be blank."
            );
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        assertThatThrownBy(() ->
            new PublishOutboxEventsCommand(
                "publisher-1",
                0,
                Duration.ofSeconds(30)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "batchSize must be positive."
            );
    }

    @Test
    void shouldRejectNonPositiveLeaseDuration() {
        assertThatThrownBy(() ->
            new PublishOutboxEventsCommand(
                "publisher-1",
                25,
                Duration.ZERO
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "leaseDuration must be positive."
            );
    }
}
