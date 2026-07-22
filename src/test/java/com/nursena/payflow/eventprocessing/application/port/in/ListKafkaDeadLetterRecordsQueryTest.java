package com.nursena.payflow.eventprocessing.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.Test;

class ListKafkaDeadLetterRecordsQueryTest {

    @Test
    void shouldCreateValidQuery() {
        KafkaDeadLetterRecordFilter filter =
            new KafkaDeadLetterRecordFilter(
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED
            );

        ListKafkaDeadLetterRecordsQuery query =
            new ListKafkaDeadLetterRecordsQuery(
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
                new ListKafkaDeadLetterRecordsQuery(
                    -1,
                    20,
                    KafkaDeadLetterRecordFilter
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
                new ListKafkaDeadLetterRecordsQuery(
                    0,
                    0,
                    KafkaDeadLetterRecordFilter
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
                new ListKafkaDeadLetterRecordsQuery(
                    0,
                    ListKafkaDeadLetterRecordsQuery
                        .MAX_SIZE + 1,
                    KafkaDeadLetterRecordFilter
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
                new ListKafkaDeadLetterRecordsQuery(
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
