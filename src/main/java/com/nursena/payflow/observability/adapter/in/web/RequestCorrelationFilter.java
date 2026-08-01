package com.nursena.payflow.observability.adapter.in.web;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import com.nursena.payflow.observability.domain.CorrelationIdGenerator;
import com.nursena.payflow.observability.domain.CorrelationIdPolicy;
import com.nursena.payflow.observability.logging.HttpRequestCompletion;
import com.nursena.payflow.observability.logging.HttpRequestOutcome;
import com.nursena.payflow.observability.logging.RequestCompletionLogger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public final class RequestCorrelationFilter
    extends OncePerRequestFilter {

    public static final String HEADER_NAME =
        "X-Correlation-ID";

    public static final String MDC_KEY =
        "correlationId";

    public static final String REQUEST_ATTRIBUTE =
        RequestCorrelationFilter.class.getName()
            + ".effectiveCorrelationId";

    public static final String UNMATCHED_ROUTE =
        "UNMATCHED";

    public static final String UNKNOWN_METHOD =
        "UNKNOWN";

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            RequestCorrelationFilter.class
        );

    private static final Pattern SAFE_METHOD =
        Pattern.compile(
            "[A-Z]{1,16}"
        );

    private static final int MAXIMUM_ROUTE_LENGTH =
        256;

    private final CorrelationIdPolicy policy;
    private final CorrelationIdGenerator generator;
    private final LongSupplier nanoTime;
    private final RequestCompletionLogger completionLogger;

    public RequestCorrelationFilter(
        CorrelationIdPolicy policy,
        CorrelationIdGenerator generator,
        LongSupplier nanoTime,
        RequestCompletionLogger completionLogger
    ) {
        this.policy =
            Objects.requireNonNull(
                policy,
                "correlation ID policy must not be null"
            );

        this.generator =
            Objects.requireNonNull(
                generator,
                "correlation ID generator must not be null"
            );

        this.nanoTime =
            Objects.requireNonNull(
                nanoTime,
                "nano-time source must not be null"
            );

        this.completionLogger =
            Objects.requireNonNull(
                completionLogger,
                "request completion logger must not be null"
            );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String effectiveCorrelationId =
            policy.effective(
                singleInboundValue(request),
                generator
            );

        request.setAttribute(
            REQUEST_ATTRIBUTE,
            effectiveCorrelationId
        );

        response.setHeader(
            HEADER_NAME,
            effectiveCorrelationId
        );

        MDC.put(
            MDC_KEY,
            effectiveCorrelationId
        );

        long startedAtNanos =
            nanoTime.getAsLong();

        boolean failed =
            false;

        try {
            filterChain.doFilter(
                request,
                response
            );
        }
        catch (
            ServletException
                | IOException
                | RuntimeException
                | Error exception
        ) {
            failed =
                true;

            throw exception;
        }
        finally {
            long completedAtNanos =
                nanoTime.getAsLong();

            writeCompletionSafely(
                request,
                response,
                startedAtNanos,
                completedAtNanos,
                failed
            );

            MDC.remove(
                MDC_KEY
            );
        }
    }

    private void writeCompletionSafely(
        HttpServletRequest request,
        HttpServletResponse response,
        long startedAtNanos,
        long completedAtNanos,
        boolean failed
    ) {
        try {
            int statusCode =
                effectiveStatusCode(
                    response.getStatus(),
                    failed
                );

            completionLogger.completed(
                new HttpRequestCompletion(
                    safeMethod(
                        request.getMethod()
                    ),
                    safeRoute(request),
                    statusCode,
                    elapsedMilliseconds(
                        startedAtNanos,
                        completedAtNanos
                    ),
                    HttpRequestOutcome.from(
                        statusCode,
                        failed
                    )
                )
            );
        }
        catch (RuntimeException loggingFailure) {
            LOGGER.warn(
                "HTTP request completion logging failed.",
                loggingFailure
            );
        }
    }

    private static int effectiveStatusCode(
        int responseStatus,
        boolean failed
    ) {
        boolean invalidStatus =
            responseStatus < 100
                || responseStatus > 599;

        if (invalidStatus) {
            return 500;
        }

        if (failed && responseStatus < 500) {
            return 500;
        }

        return responseStatus;
    }

    private static long elapsedMilliseconds(
        long startedAtNanos,
        long completedAtNanos
    ) {
        long elapsedNanos =
            completedAtNanos - startedAtNanos;

        if (elapsedNanos < 0) {
            return 0;
        }

        return TimeUnit.NANOSECONDS
            .toMillis(
                elapsedNanos
            );
    }

    private static String safeMethod(
        String method
    ) {
        if (method == null) {
            return UNKNOWN_METHOD;
        }

        String normalized =
            method.toUpperCase(
                Locale.ROOT
            );

        if (!SAFE_METHOD
            .matcher(normalized)
            .matches()) {
            return UNKNOWN_METHOD;
        }

        return normalized;
    }

    private static String safeRoute(
        HttpServletRequest request
    ) {
        Object routeAttribute =
            request.getAttribute(
                HandlerMapping
                    .BEST_MATCHING_PATTERN_ATTRIBUTE
            );

        if (routeAttribute == null) {
            return UNMATCHED_ROUTE;
        }

        String route =
            routeAttribute.toString();

        boolean unsafe =
            route.isBlank()
                || route.length() > MAXIMUM_ROUTE_LENGTH
                || route.indexOf('\r') >= 0
                || route.indexOf('\n') >= 0;

        if (unsafe) {
            return UNMATCHED_ROUTE;
        }

        return route;
    }

    private static String singleInboundValue(
        HttpServletRequest request
    ) {
        Enumeration<String> values =
            request.getHeaders(
                HEADER_NAME
            );

        boolean headerMissing =
            values == null
                || !values.hasMoreElements();

        if (headerMissing) {
            return null;
        }

        String value =
            values.nextElement();

        if (values.hasMoreElements()) {
            return null;
        }

        return value;
    }
}