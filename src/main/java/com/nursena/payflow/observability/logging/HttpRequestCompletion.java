package com.nursena.payflow.observability.logging;

import java.util.Objects;

public record HttpRequestCompletion(
    String method,
    String route,
    int statusCode,
    long durationMilliseconds,
    HttpRequestOutcome outcome
) {

    public HttpRequestCompletion {
        method =
            requireText(
                method,
                "HTTP method"
            );

        route =
            requireText(
                route,
                "HTTP route"
            );

        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                "HTTP status code must be between 100 and 599"
            );
        }

        if (durationMilliseconds < 0) {
            throw new IllegalArgumentException(
                "HTTP duration must not be negative"
            );
        }

        outcome =
            Objects.requireNonNull(
                outcome,
                "HTTP request outcome must not be null"
            );
    }

    private static String requireText(
        String value,
        String field
    ) {
        Objects.requireNonNull(
            value,
            field + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                field + " must not be blank"
            );
        }

        return value;
    }
}