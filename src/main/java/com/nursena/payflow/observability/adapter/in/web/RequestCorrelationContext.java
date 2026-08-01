package com.nursena.payflow.observability.adapter.in.web;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestCorrelationContext {

    private RequestCorrelationContext() {
    }

    public static String require(
        HttpServletRequest request
    ) {
        Objects.requireNonNull(
            request,
            "HTTP request must not be null"
        );

        Object value =
            request.getAttribute(
                RequestCorrelationFilter
                    .REQUEST_ATTRIBUTE
            );

        boolean validValue =
            value instanceof String identifier
                && !identifier.isBlank();

        if (!validValue) {
            throw new IllegalStateException(
                "effective correlation ID is unavailable"
            );
        }

        return (String) value;
    }
}