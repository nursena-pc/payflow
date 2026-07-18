package com.nursena.payflow.outbox.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublishOutboxEventsResultTest {

    @Test
    void shouldCreateBalancedResult() {
        PublishOutboxEventsResult result =
            new PublishOutboxEventsResult(
                5,
                2,
                1,
                1,
                1
            );

        assertThat(result.claimedCount())
            .isEqualTo(5);

        assertThat(result.publishedCount())
            .isEqualTo(2);

        assertThat(result.retriedCount())
            .isEqualTo(1);

        assertThat(result.failedCount())
            .isEqualTo(1);

        assertThat(result.unresolvedCount())
            .isEqualTo(1);
    }

    @Test
    void shouldCreateEmptyResult() {
        assertThat(
            PublishOutboxEventsResult.empty()
        ).isEqualTo(
            new PublishOutboxEventsResult(
                0,
                0,
                0,
                0,
                0
            )
        );
    }

    @Test
    void shouldRejectUnbalancedOutcomeCounts() {
        assertThatThrownBy(() ->
            new PublishOutboxEventsResult(
                5,
                2,
                1,
                1,
                0
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Outcome counts must equal "
                    + "claimedCount."
            );
    }
}
