package com.nursena.payflow.observability.adapter.in.web;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;

import com.nursena.payflow.observability.domain.CorrelationIdGenerator;
import com.nursena.payflow.observability.domain.CorrelationIdPolicy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestCorrelationFilter
    extends OncePerRequestFilter {

    public static final String HEADER_NAME =
        "X-Correlation-ID";

    public static final String MDC_KEY =
        "correlationId";

    public static final String REQUEST_ATTRIBUTE =
        RequestCorrelationFilter.class.getName()
            + ".effectiveCorrelationId";

    private final CorrelationIdPolicy policy;
    private final CorrelationIdGenerator generator;

    public RequestCorrelationFilter(
        CorrelationIdPolicy policy,
        CorrelationIdGenerator generator
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

        try {
            filterChain.doFilter(
                request,
                response
            );
        }
        finally {
            MDC.remove(
                MDC_KEY
            );
        }
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