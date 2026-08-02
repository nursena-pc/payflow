package com.nursena.payflow.observability.logging;

public enum HttpRequestOutcome {
    SUCCESS,
    CLIENT_ERROR,
    SERVER_ERROR;

    public static HttpRequestOutcome from(
        int statusCode,
        boolean failed
    ) {
        if (failed || statusCode >= 500) {
            return SERVER_ERROR;
        }

        if (statusCode >= 400) {
            return CLIENT_ERROR;
        }

        return SUCCESS;
    }
}