package com.nursena.payflow.observability.logging;

@FunctionalInterface
public interface RequestCompletionLogger {

    void completed(
        HttpRequestCompletion completion
    );
}