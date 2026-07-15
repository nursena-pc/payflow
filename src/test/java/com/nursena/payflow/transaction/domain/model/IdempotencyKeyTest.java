package com.nursena.payflow.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.transaction.domain.exception.InvalidIdempotencyKeyException;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void shouldTrimValidValue() {
        IdempotencyKey key =
            new IdempotencyKey("  transfer-request-123  ");

        assertThat(key.value())
            .isEqualTo("transfer-request-123");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() ->
            new IdempotencyKey("   ")
        )
            .isInstanceOf(
                InvalidIdempotencyKeyException.class
            )
            .hasMessage(
                "Idempotency key must contain "
                    + "between 1 and 100 characters."
            );
    }

    @Test
    void shouldRejectValueLongerThanDatabaseLimit() {
        String value = "a".repeat(
            IdempotencyKey.MAX_LENGTH + 1
        );

        assertThatThrownBy(() ->
            new IdempotencyKey(value)
        )
            .isInstanceOf(
                InvalidIdempotencyKeyException.class
            );
    }
}
