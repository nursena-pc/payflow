package com.nursena.payflow.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpRequestOutcomeTest {

    @Test
    void shouldClassifySuccessfulResponse() {
        assertThat(
            HttpRequestOutcome.from(
                200,
                false
            )
        )
            .isEqualTo(
                HttpRequestOutcome.SUCCESS
            );
    }

    @Test
    void shouldClassifyRedirectionAsSuccess() {
        assertThat(
            HttpRequestOutcome.from(
                302,
                false
            )
        )
            .isEqualTo(
                HttpRequestOutcome.SUCCESS
            );
    }

    @Test
    void shouldClassifyClientError() {
        assertThat(
            HttpRequestOutcome.from(
                422,
                false
            )
        )
            .isEqualTo(
                HttpRequestOutcome.CLIENT_ERROR
            );
    }

    @Test
    void shouldClassifyServerError() {
        assertThat(
            HttpRequestOutcome.from(
                503,
                false
            )
        )
            .isEqualTo(
                HttpRequestOutcome.SERVER_ERROR
            );
    }

    @Test
    void shouldPrioritizeUnhandledFailure() {
        assertThat(
            HttpRequestOutcome.from(
                200,
                true
            )
        )
            .isEqualTo(
                HttpRequestOutcome.SERVER_ERROR
            );
    }
}