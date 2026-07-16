package com.nursena.payflow.transaction.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TransactionHistoryFilterTest {

    private static final Instant FROM =
        Instant.parse(
            "2026-07-01T00:00:00Z"
        );

    private static final Instant TO =
        Instant.parse(
            "2026-08-01T00:00:00Z"
        );

    @Test
    void shouldCreateUnfilteredFilter() {
        TransactionHistoryFilter filter =
            TransactionHistoryFilter.unfiltered();

        assertThat(filter.direction())
            .isNull();

        assertThat(filter.status())
            .isNull();

        assertThat(filter.from())
            .isNull();

        assertThat(filter.to())
            .isNull();
    }

    @Test
    void shouldAcceptValidDateRange() {
        TransactionHistoryFilter filter =
            new TransactionHistoryFilter(
                TransactionDirection.INCOMING,
                null,
                FROM,
                TO
            );

        assertThat(filter.from())
            .isEqualTo(FROM);

        assertThat(filter.to())
            .isEqualTo(TO);
    }

    @Test
    void shouldAllowEqualDateBoundaries() {
        TransactionHistoryFilter filter =
            new TransactionHistoryFilter(
                null,
                null,
                FROM,
                FROM
            );

        assertThat(filter.from())
            .isEqualTo(filter.to());
    }

    @Test
    void shouldRejectReversedDateRange() {
        assertThatThrownBy(() ->
            new TransactionHistoryFilter(
                null,
                null,
                TO,
                FROM
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "from must not be after to"
            );
    }
}