package com.nursena.payflow.observability.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.nursena.payflow.common.exception.BusinessRuleException;
import com.nursena.payflow.common.exception.GlobalExceptionHandler;
import com.nursena.payflow.observability.domain.CorrelationIdPolicy;
import com.nursena.payflow.observability.logging.HttpRequestCompletion;
import com.nursena.payflow.observability.logging.HttpRequestOutcome;
import com.nursena.payflow.observability.logging.RequestCompletionLogger;
import com.nursena.payflow.observability.logging.Slf4jRequestCompletionLogger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestCompletionHttpIntegrationTest {

    private static final String GENERATED_ID =
        "generated-completion-123";

    private final List<HttpRequestCompletion>
        completionEvents =
            new ArrayList<>();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AtomicLong nanoTime =
            new AtomicLong(
                1_000_000L
            );

        Slf4jRequestCompletionLogger
            structuredLogger =
                new Slf4jRequestCompletionLogger();

        RequestCompletionLogger completionLogger =
            completion -> {
                completionEvents.add(
                    completion
                );

                structuredLogger.completed(
                    completion
                );
            };

        RequestCorrelationFilter filter =
            new RequestCorrelationFilter(
                new CorrelationIdPolicy(),
                () -> GENERATED_ID,
                () -> nanoTime.getAndAdd(
                    5_000_000L
                ),
                completionLogger
            );

        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    new TestController()
                )
                .setControllerAdvice(
                    new GlobalExceptionHandler()
                )
                .addFilters(filter)
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldLogSuccessfulRequestOnceWithRouteTemplate()
        throws Exception {
        mockMvc.perform(
            get(
                "/test/completion/items/{itemId}",
                "wallet-123"
            )
                .queryParam(
                    "token",
                    "query-secret"
                )
                .header(
                    "Authorization",
                    "Bearer header-secret"
                )
                .header(
                    RequestCorrelationFilter.HEADER_NAME,
                    "request-123"
                )
        )
            .andExpect(
                status().isOk()
            );

        HttpRequestCompletion completion =
            singleCompletion();

        assertThat(
            completion.method()
        )
            .isEqualTo("GET");

        assertThat(
            completion.route()
        )
            .isEqualTo(
                "/test/completion/items/{itemId}"
            );

        assertThat(
            completion.statusCode()
        )
            .isEqualTo(200);

        assertThat(
            completion.durationMilliseconds()
        )
            .isEqualTo(5);

        assertThat(
            completion.outcome()
        )
            .isEqualTo(
                HttpRequestOutcome.SUCCESS
            );

        assertThat(
            completion.toString()
        )
            .doesNotContain(
                "wallet-123",
                "query-secret",
                "header-secret",
                "Authorization",
                "token"
            );
    }

    @Test
    void shouldLogValidationErrorOnce()
        throws Exception {
        mockMvc.perform(
            post("/test/completion/validation")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "value": ""
                    }
                    """
                )
        )
            .andExpect(
                status().isBadRequest()
            );

        HttpRequestCompletion completion =
            singleCompletion();

        assertThat(
            completion.route()
        )
            .isEqualTo(
                "/test/completion/validation"
            );

        assertThat(
            completion.statusCode()
        )
            .isEqualTo(400);

        assertThat(
            completion.outcome()
        )
            .isEqualTo(
                HttpRequestOutcome.CLIENT_ERROR
            );
    }

    @Test
    void shouldLogBusinessErrorOnce()
        throws Exception {
        mockMvc.perform(
            get("/test/completion/business")
        )
            .andExpect(
                status().isUnprocessableEntity()
            );

        HttpRequestCompletion completion =
            singleCompletion();

        assertThat(
            completion.route()
        )
            .isEqualTo(
                "/test/completion/business"
            );

        assertThat(
            completion.statusCode()
        )
            .isEqualTo(422);

        assertThat(
            completion.outcome()
        )
            .isEqualTo(
                HttpRequestOutcome.CLIENT_ERROR
            );
    }

    @Test
    void shouldLogUnmatchedRequestWithoutRawPath()
        throws Exception {
        mockMvc.perform(
            get("/private/customer-123")
                .queryParam(
                    "password",
                    "query-secret"
                )
        )
            .andExpect(
                status().isNotFound()
            );

        HttpRequestCompletion completion =
            singleCompletion();

        assertThat(
            completion.route()
        )
            .isEqualTo(
                RequestCorrelationFilter
                    .UNMATCHED_ROUTE
            );

        assertThat(
            completion.statusCode()
        )
            .isEqualTo(404);

        assertThat(
            completion.toString()
        )
            .doesNotContain(
                "private",
                "customer-123",
                "password",
                "query-secret"
            );
    }

    @Test
    void shouldLogUnhandledFailureAsServerError() {
        try {
            mockMvc.perform(
                get("/test/completion/failure")
            )
                .andReturn();
        }
        catch (Exception expected) {
        }

        HttpRequestCompletion completion =
            singleCompletion();

        assertThat(
            completion.route()
        )
            .isEqualTo(
                "/test/completion/failure"
            );

        assertThat(
            completion.statusCode()
        )
            .isEqualTo(500);

        assertThat(
            completion.outcome()
        )
            .isEqualTo(
                HttpRequestOutcome.SERVER_ERROR
            );
    }

    @Test
    void shouldClearRequestAndCompletionMdcAfterRequest()
        throws Exception {
        mockMvc.perform(
            get(
                "/test/completion/items/{itemId}",
                "wallet-123"
            )
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            MDC.get(
                RequestCorrelationFilter.MDC_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.EVENT_KEY
            )
        )
            .isNull();

        assertThat(
            MDC.get(
                Slf4jRequestCompletionLogger.ROUTE_KEY
            )
        )
            .isNull();

        assertThat(
            completionEvents
        )
            .hasSize(1);
    }

    private HttpRequestCompletion singleCompletion() {
        assertThat(
            completionEvents
        )
            .hasSize(1);

        return completionEvents.get(0);
    }

    @RestController
    @RequestMapping("/test/completion")
    static class TestController {

        @GetMapping("/items/{itemId}")
        Map<String, String> item(
            @PathVariable
            String itemId
        ) {
            return Map.of(
                "itemId",
                itemId
            );
        }

        @PostMapping("/validation")
        void validation(
            @Valid
            @RequestBody
            TestRequest request
        ) {
        }

        @GetMapping("/business")
        void business() {
            throw new TestBusinessRuleException();
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException(
                "simulated unhandled failure"
            );
        }
    }

    record TestRequest(
        @NotBlank(
            message = "Value is required."
        )
        String value
    ) {
    }

    static final class TestBusinessRuleException
        extends BusinessRuleException {

        TestBusinessRuleException() {
            super(
                "TEST_BUSINESS_RULE",
                "Simulated business-rule failure."
            );
        }
    }
}