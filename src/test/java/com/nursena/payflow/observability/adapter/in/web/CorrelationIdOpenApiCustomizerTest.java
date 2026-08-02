package com.nursena.payflow.observability.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class CorrelationIdOpenApiCustomizerTest {

    private final CorrelationIdOpenApiCustomizer
        customizer =
            new CorrelationIdOpenApiCustomizer();

    @Test
    void shouldAddCorrelationHeaderToEveryResponse() {
        ApiResponse getResponse =
            new ApiResponse();

        getResponse.setDescription(
            "GET response"
        );

        ApiResponse postResponse =
            new ApiResponse();

        postResponse.setDescription(
            "POST response"
        );

        OpenAPI openApi =
            openApiWithResponses(
                getResponse,
                postResponse
            );

        customizer.customise(
            openApi
        );

        assertThat(
            getResponse.getHeaders()
        )
            .containsKey(
                RequestCorrelationFilter.HEADER_NAME
            );

        assertThat(
            postResponse.getHeaders()
        )
            .containsKey(
                RequestCorrelationFilter.HEADER_NAME
            );
    }

    @Test
    void shouldExposeBoundedRequiredHeaderSchema() {
        ApiResponse response =
            new ApiResponse();

        response.setDescription(
            "Successful response"
        );

        OpenAPI openApi =
            openApiWithResponses(
                response,
                null
            );

        customizer.customise(
            openApi
        );

        Header header =
            response.getHeaders()
                .get(
                    RequestCorrelationFilter.HEADER_NAME
                );

        assertThat(header)
            .isNotNull();

        assertThat(
            header.getRequired()
        )
            .isTrue();

        assertThat(
            header.getDescription()
        )
            .isEqualTo(
                CorrelationIdOpenApiCustomizer.DESCRIPTION
            );

        assertThat(
            header.getSchema()
                .getMaxLength()
        )
            .isEqualTo(
                CorrelationIdOpenApiCustomizer.MAXIMUM_LENGTH
            );

        assertThat(
            header.getSchema()
                .getPattern()
        )
            .isEqualTo(
                CorrelationIdOpenApiCustomizer.VALUE_PATTERN
            );
    }

    @Test
    void shouldPreserveResponseDescription() {
        ApiResponse response =
            new ApiResponse();

        response.setDescription(
            "Existing contract"
        );

        OpenAPI openApi =
            openApiWithResponses(
                response,
                null
            );

        customizer.customise(
            openApi
        );

        assertThat(
            response.getDescription()
        )
            .isEqualTo(
                "Existing contract"
            );
    }

    @Test
    void shouldIgnoreIncompleteOpenApiModels() {
        OpenAPI emptyOpenApi =
            new OpenAPI();

        OpenAPI operationWithoutResponses =
            new OpenAPI();

        Operation operation =
            new Operation();

        PathItem pathItem =
            new PathItem();

        pathItem.setGet(
            operation
        );

        Paths paths =
            new Paths();

        paths.addPathItem(
            "/test",
            pathItem
        );

        operationWithoutResponses.setPaths(
            paths
        );

        assertThatCode(
            () -> customizer.customise(
                emptyOpenApi
            )
        )
            .doesNotThrowAnyException();

        assertThatCode(
            () -> customizer.customise(
                operationWithoutResponses
            )
        )
            .doesNotThrowAnyException();
    }

    private static OpenAPI openApiWithResponses(
        ApiResponse getResponse,
        ApiResponse postResponse
    ) {
        ApiResponses getResponses =
            new ApiResponses();

        getResponses.addApiResponse(
            "200",
            getResponse
        );

        Operation getOperation =
            new Operation();

        getOperation.setResponses(
            getResponses
        );

        PathItem pathItem =
            new PathItem();

        pathItem.setGet(
            getOperation
        );

        if (postResponse != null) {
            ApiResponses postResponses =
                new ApiResponses();

            postResponses.addApiResponse(
                "201",
                postResponse
            );

            Operation postOperation =
                new Operation();

            postOperation.setResponses(
                postResponses
            );

            pathItem.setPost(
                postOperation
            );
        }

        Paths paths =
            new Paths();

        paths.addPathItem(
            "/test",
            pathItem
        );

        OpenAPI openApi =
            new OpenAPI();

        openApi.setPaths(
            paths
        );

        return openApi;
    }
}