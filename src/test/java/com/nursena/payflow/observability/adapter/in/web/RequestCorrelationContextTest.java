package com.nursena.payflow.observability.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestCorrelationContextTest {

    @Test
    void shouldReadEffectiveCorrelationId() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setAttribute(
            RequestCorrelationFilter
                .REQUEST_ATTRIBUTE,
            "request-123"
        );

        assertThat(
            RequestCorrelationContext
                .require(request)
        )
            .isEqualTo(
                "request-123"
            );
    }

    @Test
    void shouldRejectMissingAttribute() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        assertThatThrownBy(
            () ->
                RequestCorrelationContext
                    .require(request)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "effective correlation ID is unavailable"
            );
    }

    @Test
    void shouldRejectNonStringAttribute() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setAttribute(
            RequestCorrelationFilter
                .REQUEST_ATTRIBUTE,
            123
        );

        assertThatThrownBy(
            () ->
                RequestCorrelationContext
                    .require(request)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "effective correlation ID is unavailable"
            );
    }

    @Test
    void shouldRejectBlankAttribute() {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setAttribute(
            RequestCorrelationFilter
                .REQUEST_ATTRIBUTE,
            " "
        );

        assertThatThrownBy(
            () ->
                RequestCorrelationContext
                    .require(request)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "effective correlation ID is unavailable"
            );
    }
}