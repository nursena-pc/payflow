package com.nursena.payflow.observability.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import com.nursena.payflow.observability.domain.CorrelationIdPolicy;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPreserveAcceptedInboundValueDuringRequest()
        throws Exception {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.addHeader(
            RequestCorrelationFilter.HEADER_NAME,
            "request-123"
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get(
                        RequestCorrelationFilter.MDC_KEY
                    )
                )
                    .isEqualTo(
                        "request-123"
                    );

                assertThat(
                    ((HttpServletRequest) servletRequest)
                        .getAttribute(
                            RequestCorrelationFilter
                                .REQUEST_ATTRIBUTE
                        )
                )
                    .isEqualTo(
                        "request-123"
                    );
            }
        );

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "request-123"
            );

        assertThat(
            MDC.get(
                RequestCorrelationFilter.MDC_KEY
            )
        )
            .isNull();
    }

    @Test
    void shouldGenerateWhenHeaderIsMissing()
        throws Exception {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get(
                        RequestCorrelationFilter.MDC_KEY
                    )
                )
                    .isEqualTo(
                        "generated-123"
                    );
            }
        );

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "generated-123"
            );
    }

    @Test
    void shouldReplaceLineBreakingHeader()
        throws Exception {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.addHeader(
            RequestCorrelationFilter.HEADER_NAME,
            "request-123\r\nforged"
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get(
                        RequestCorrelationFilter.MDC_KEY
                    )
                )
                    .isEqualTo(
                        "generated-123"
                    );
            }
        );

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "generated-123"
            );
    }

    @Test
    void shouldReplaceOversizedHeader()
        throws Exception {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.addHeader(
            RequestCorrelationFilter.HEADER_NAME,
            "a".repeat(
                CorrelationIdPolicy
                    .MAXIMUM_LENGTH
                    + 1
            )
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get(
                        RequestCorrelationFilter.MDC_KEY
                    )
                )
                    .isEqualTo(
                        "generated-123"
                    );
            }
        );

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "generated-123"
            );
    }

    @Test
    void shouldReplaceDuplicateHeaderValues()
        throws Exception {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.addHeader(
            RequestCorrelationFilter.HEADER_NAME,
            "request-123"
        );

        request.addHeader(
            RequestCorrelationFilter.HEADER_NAME,
            "request-456"
        );

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        filter.doFilter(
            request,
            response,
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get(
                        RequestCorrelationFilter.MDC_KEY
                    )
                )
                    .isEqualTo(
                        "generated-123"
                    );
            }
        );

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "generated-123"
            );
    }

    @Test
    void shouldRemoveCorrelationContextWhenChainFails() {
        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        MockHttpServletRequest request =
            new MockHttpServletRequest();

        MockHttpServletResponse response =
            new MockHttpServletResponse();

        assertThatThrownBy(
            () -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    throw new ServletException(
                        "simulated failure"
                    );
                }
            )
        )
            .isInstanceOf(
                ServletException.class
            )
            .hasMessage(
                "simulated failure"
            );

        assertThat(
            MDC.get(
                RequestCorrelationFilter.MDC_KEY
            )
        )
            .isNull();

        assertThat(
            response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
            )
        )
            .isEqualTo(
                "generated-123"
            );
    }

    @Test
    void shouldPreserveUnrelatedMdcFields()
        throws ServletException, IOException {
        MDC.put(
            "unrelated",
            "retained"
        );

        RequestCorrelationFilter filter =
            filter(
                "generated-123"
            );

        filter.doFilter(
            new MockHttpServletRequest(),
            new MockHttpServletResponse(),
            (servletRequest, servletResponse) -> {
                assertThat(
                    MDC.get("unrelated")
                )
                    .isEqualTo(
                        "retained"
                    );
            }
        );

        assertThat(
            MDC.get("unrelated")
        )
            .isEqualTo(
                "retained"
            );

        assertThat(
            MDC.get(
                RequestCorrelationFilter.MDC_KEY
            )
        )
            .isNull();
    }

    private static RequestCorrelationFilter filter(
        String generatedValue
    ) {
        return new RequestCorrelationFilter(
            new CorrelationIdPolicy(),
            () -> generatedValue
        );
    }
}