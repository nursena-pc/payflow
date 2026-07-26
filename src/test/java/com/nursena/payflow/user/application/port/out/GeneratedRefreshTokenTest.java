package com.nursena.payflow.user.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeneratedRefreshTokenTest {

    @Test
    void shouldCreateGeneratedRefreshToken() {
        GeneratedRefreshToken token =
            new GeneratedRefreshToken(
                "opaque-refresh-token"
            );

        assertThat(token.value())
            .isEqualTo(
                "opaque-refresh-token"
            );
    }

    @Test
    void shouldRedactTokenFromStringRepresentation() {
        GeneratedRefreshToken token =
            new GeneratedRefreshToken(
                "secret-refresh-token"
            );

        assertThat(token.toString())
            .isEqualTo(
                "GeneratedRefreshToken[redacted]"
            );

        assertThat(token.toString())
            .doesNotContain(
                "secret-refresh-token"
            );
    }

    @Test
    void shouldRejectBlankGeneratedRefreshToken() {
        assertThatThrownBy(() ->
            new GeneratedRefreshToken(" ")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "value must not be blank"
            );
    }
}
