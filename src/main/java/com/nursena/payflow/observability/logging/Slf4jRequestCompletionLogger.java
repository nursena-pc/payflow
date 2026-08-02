package com.nursena.payflow.observability.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class Slf4jRequestCompletionLogger
    implements RequestCompletionLogger {

    public static final String EVENT_KEY =
        "event";

    public static final String METHOD_KEY =
        "http.method";

    public static final String ROUTE_KEY =
        "http.route";

    public static final String STATUS_CODE_KEY =
        "http.status_code";

    public static final String DURATION_KEY =
        "duration_ms";

    public static final String OUTCOME_KEY =
        "outcome";

    public static final String COMPLETION_EVENT =
        "http.request.completed";

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            Slf4jRequestCompletionLogger.class
        );

    private static final List<String> TEMPORARY_KEYS =
        List.of(
            EVENT_KEY,
            METHOD_KEY,
            ROUTE_KEY,
            STATUS_CODE_KEY,
            DURATION_KEY,
            OUTCOME_KEY
        );

    @Override
    public void completed(
        HttpRequestCompletion completion
    ) {
        Objects.requireNonNull(
            completion,
            "HTTP request completion must not be null"
        );

        Map<String, String> previousValues =
            capturePreviousValues();

        try {
            putCompletionFields(
                completion
            );

            LOGGER.info(
                "HTTP request completed: method={}, route={}, "
                    + "status={}, durationMs={}, outcome={}.",
                completion.method(),
                completion.route(),
                completion.statusCode(),
                completion.durationMilliseconds(),
                completion.outcome()
            );
        }
        finally {
            restorePreviousValues(
                previousValues
            );
        }
    }

    private static Map<String, String>
        capturePreviousValues() {
        Map<String, String> previousValues =
            new HashMap<>();

        for (String key : TEMPORARY_KEYS) {
            previousValues.put(
                key,
                MDC.get(key)
            );
        }

        return previousValues;
    }

    private static void putCompletionFields(
        HttpRequestCompletion completion
    ) {
        MDC.put(
            EVENT_KEY,
            COMPLETION_EVENT
        );

        MDC.put(
            METHOD_KEY,
            completion.method()
        );

        MDC.put(
            ROUTE_KEY,
            completion.route()
        );

        MDC.put(
            STATUS_CODE_KEY,
            Integer.toString(
                completion.statusCode()
            )
        );

        MDC.put(
            DURATION_KEY,
            Long.toString(
                completion.durationMilliseconds()
            )
        );

        MDC.put(
            OUTCOME_KEY,
            completion.outcome()
                .name()
        );
    }

    private static void restorePreviousValues(
        Map<String, String> previousValues
    ) {
        for (String key : TEMPORARY_KEYS) {
            String previousValue =
                previousValues.get(key);

            if (previousValue == null) {
                MDC.remove(key);
            }
            else {
                MDC.put(
                    key,
                    previousValue
                );
            }
        }
    }
}