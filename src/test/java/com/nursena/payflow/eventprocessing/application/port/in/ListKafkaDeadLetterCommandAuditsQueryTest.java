package com.nursena.payflow.eventprocessing.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import org.junit.jupiter.api.Test;

class ListKafkaDeadLetterCommandAuditsQueryTest {

    @Test
    void shouldCreateValidQuery() {
        KafkaDeadLetterCommandAuditFilter filter =
            KafkaDeadLetterCommandAuditFilter
                .unfiltered();

        ListKafkaDeadLetterCommandAuditsQuery query =
            new ListKafkaDeadLetterCommandAuditsQuery(
                2,
                50,
                filter
            );

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.filter()).isSameAs(filter);
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(
            () ->
                new ListKafkaDeadLetterCommandAuditsQuery(
                    -1,
                    20,
                    KafkaDeadLetterCommandAuditFilter
                        .unfiltered()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "page must not be negative"
            );
    }

    @Test
    void shouldRejectZeroPageSize() {
        assertThatThrownBy(
            () ->
                new ListKafkaDeadLetterCommandAuditsQuery(
                    0,
                    0,
                    KafkaDeadLetterCommandAuditFilter
                        .unfiltered()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "size must be greater than zero"
            );
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() {
        assertThatThrownBy(
            () ->
                new ListKafkaDeadLetterCommandAuditsQuery(
                    0,
                    ListKafkaDeadLetterCommandAuditsQuery
                        .MAX_SIZE + 1,
                    KafkaDeadLetterCommandAuditFilter
                        .unfiltered()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "size must not exceed 100"
            );
    }

    @Test
    void shouldRejectNullFilter() {
        assertThatThrownBy(
            () ->
                new ListKafkaDeadLetterCommandAuditsQuery(
                    0,
                    20,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "filter must not be null"
            );
    }
}
