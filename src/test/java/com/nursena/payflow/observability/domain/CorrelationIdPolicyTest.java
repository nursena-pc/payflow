package com.nursena.payflow.observability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorrelationIdPolicyTest {

    private final CorrelationIdPolicy policy =
        new CorrelationIdPolicy();

    @Test
    void shouldAcceptBoundedAsciiIdentifiers() {
        assertThat(
            policy.isAccepted(
                "request-123_ABC.trace:7"
            )
        )
            .isTrue();

        assertThat(
            policy.isAccepted(
                "550e8400-e29b-41d4-a716-446655440000"
            )
        )
            .isTrue();
    }

    @Test
    void shouldRejectMissingBlankOrWhitespaceBearingValues() {
        assertThat(
            policy.isAccepted(null)
        )
            .isFalse();

        assertThat(
            policy.isAccepted("")
        )
            .isFalse();

        assertThat(
            policy.isAccepted(
                " request-123"
            )
        )
            .isFalse();

        assertThat(
            policy.isAccepted(
                "request 123"
            )
        )
            .isFalse();
    }

    @Test
    void shouldRejectLineBreaksAndUnsupportedCharacters() {
        assertThat(
            policy.isAccepted(
                "request-123\r\nforged-entry"
            )
        )
            .isFalse();

        assertThat(
            policy.isAccepted(
                "request/123"
            )
        )
            .isFalse();

        assertThat(
            policy.isAccepted(
                "istek-ç"
            )
        )
            .isFalse();
    }

    @Test
    void shouldRejectOversizedValues() {
        assertThat(
            policy.isAccepted(
                "a".repeat(
                    CorrelationIdPolicy
                        .MAXIMUM_LENGTH
                )
            )
        )
            .isTrue();

        assertThat(
            policy.isAccepted(
                "a".repeat(
                    CorrelationIdPolicy
                        .MAXIMUM_LENGTH
                        + 1
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldPreserveAcceptedInboundValue() {
        String effective =
            policy.effective(
                "request-123",
                () -> "generated-456"
            );

        assertThat(effective)
            .isEqualTo(
                "request-123"
            );
    }

    @Test
    void shouldGenerateForMissingOrRejectedInboundValue() {
        assertThat(
            policy.effective(
                null,
                () -> "generated-123"
            )
        )
            .isEqualTo(
                "generated-123"
            );

        assertThat(
            policy.effective(
                "bad value",
                () -> "generated-456"
            )
        )
            .isEqualTo(
                "generated-456"
            );
    }

    @Test
    void shouldRejectInvalidGeneratedValue() {
        assertThatThrownBy(
            () -> policy.effective(
                null,
                () -> "invalid generated value"
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "generated correlation ID violates the policy"
            );
    }
}