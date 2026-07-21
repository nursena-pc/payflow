package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransferCompletedKafkaDeadLetterIntakePropertiesTest {

    @Test
    void shouldCreateValidIntakeProperties() {
        TransferCompletedKafkaDeadLetterIntakeProperties
            properties =
            new
                TransferCompletedKafkaDeadLetterIntakeProperties(
                true,
                "payflow-transfer-completed"
                    + "-dlt-intake-v1"
            );

        assertThat(properties.enabled())
            .isTrue();

        assertThat(properties.groupId())
            .isEqualTo(
                "payflow-transfer-completed"
                    + "-dlt-intake-v1"
            );
    }

    @Test
    void shouldRejectBlankGroupId() {
        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaDeadLetterIntakeProperties(
                true,
                " "
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "groupId must not be blank."
            );
    }

    @Test
    void shouldRejectGroupIdAboveMaximumLength() {
        assertThatThrownBy(
            () -> new
                TransferCompletedKafkaDeadLetterIntakeProperties(
                true,
                "g".repeat(256)
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "groupId must not exceed "
                    + "255 characters."
            );
    }
}
