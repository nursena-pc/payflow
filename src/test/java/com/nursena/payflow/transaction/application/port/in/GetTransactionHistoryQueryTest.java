package com.nursena.payflow.transaction.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GetTransactionHistoryQueryTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    @Test
    void shouldCreateValidQuery() {
        GetTransactionHistoryQuery query =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20
            );

        assertThat(query.ownerId())
            .isEqualTo(OWNER_ID);

        assertThat(query.page())
            .isZero();

        assertThat(query.size())
            .isEqualTo(20);
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() ->
            new GetTransactionHistoryQuery(
                OWNER_ID,
                -1,
                20
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
    void shouldRejectZeroSize() {
        assertThatThrownBy(() ->
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                0
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "size must be between 1 and 100"
            );
    }

    @Test
    void shouldRejectSizeAboveMaximum() {
        assertThatThrownBy(() ->
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                101
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "size must be between 1 and 100"
            );
    }
}
