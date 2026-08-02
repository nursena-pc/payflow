package com.nursena.payflow.observability.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.nursena.payflow.common.exception.BusinessRuleException;
import com.nursena.payflow.common.exception.GlobalExceptionHandler;
import com.nursena.payflow.observability.domain.CorrelationIdPolicy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestCorrelationHttpIntegrationTest {

    private static final String GENERATED_ID =
        "generated-123";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RequestCorrelationFilter filter =
            new RequestCorrelationFilter(
                new CorrelationIdPolicy(),
                () -> GENERATED_ID,
                System::nanoTime,
                completion -> {
                }
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
    void shouldExposeAcceptedCorrelationIdOnSuccessfulResponse()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/success")
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "request-123"
                )
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "request-123"
                )
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value("request-123")
            );
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/success")
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    GENERATED_ID
                )
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value(GENERATED_ID)
            );
    }

    @Test
    void shouldIncludeCorrelationIdInBusinessErrorBody()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/business")
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "business-123"
                )
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "business-123"
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("TEST_BUSINESS_RULE")
            )
            .andExpect(
                jsonPath("$.path")
                    .value(
                        "/test/correlation/business"
                    )
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value("business-123")
            )
            .andExpect(
                jsonPath("$.violations")
                    .isEmpty()
            );
    }

    @Test
    void shouldIncludeCorrelationIdInValidationErrorBody()
        throws Exception {
        mockMvc.perform(
            post("/test/correlation/validation")
                .contentType(APPLICATION_JSON)
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "validation-123"
                )
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
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "validation-123"
                )
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value("validation-123")
            )
            .andExpect(
                jsonPath("$.violations[0].field")
                    .value("value")
            )
            .andExpect(
                jsonPath("$.violations[0].message")
                    .value("Value is required.")
            );
    }

    @Test
    void shouldReplaceMalformedInboundValue()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/business")
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "bad value"
                )
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    GENERATED_ID
                )
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value(GENERATED_ID)
            );
    }

    @Test
    void shouldReplaceDuplicateInboundValues()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/business")
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "request-123",
                    "request-456"
                )
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                header().string(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    GENERATED_ID
                )
            )
            .andExpect(
                jsonPath("$.correlationId")
                    .value(GENERATED_ID)
            );
    }

    @Test
    void shouldClearMdcAfterCompletedRequest()
        throws Exception {
        mockMvc.perform(
            get("/test/correlation/success")
                .header(
                    RequestCorrelationFilter
                        .HEADER_NAME,
                    "request-123"
                )
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            MDC.get(
                RequestCorrelationFilter
                    .MDC_KEY
            )
        )
            .isNull();
    }

    @RestController
    @RequestMapping("/test/correlation")
    static class TestController {

        @GetMapping("/success")
        Map<String, String> success(
            HttpServletRequest request
        ) {
            return Map.of(
                "correlationId",
                RequestCorrelationContext
                    .require(request)
            );
        }

        @GetMapping("/business")
        Map<String, String> business() {
            throw new TestBusinessRuleException();
        }

        @PostMapping("/validation")
        void validation(
            @Valid
            @RequestBody
            TestRequest request
        ) {
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