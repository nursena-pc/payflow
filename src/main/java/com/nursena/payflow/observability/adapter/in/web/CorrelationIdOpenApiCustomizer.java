package com.nursena.payflow.observability.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;

public final class CorrelationIdOpenApiCustomizer
    implements OpenApiCustomizer {

    public static final int MAXIMUM_LENGTH =
        64;

    public static final String VALUE_PATTERN =
        "^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$";

    public static final String DESCRIPTION =
        "Effective server-owned request correlation identifier. "
            + "A valid inbound value is echoed; otherwise PayFlow "
            + "generates a replacement identifier.";

    @Override
    public void customise(
        OpenAPI openApi
    ) {
        Objects.requireNonNull(
            openApi,
            "OpenAPI model must not be null"
        );

        Paths paths =
            openApi.getPaths();

        if (paths == null) {
            return;
        }

        for (PathItem pathItem : paths.values()) {
            addToPathItem(
                pathItem
            );
        }
    }

    private static void addToPathItem(
        PathItem pathItem
    ) {
        if (pathItem == null) {
            return;
        }

        List<Operation> operations =
            pathItem.readOperations();

        if (operations == null) {
            return;
        }

        for (Operation operation : operations) {
            addToOperation(
                operation
            );
        }
    }

    private static void addToOperation(
        Operation operation
    ) {
        if (operation == null) {
            return;
        }

        ApiResponses responses =
            operation.getResponses();

        if (responses == null) {
            return;
        }

        for (
            Map.Entry<String, ApiResponse> entry
                : responses.entrySet()
        ) {
            ApiResponse response =
                entry.getValue();

            if (response != null) {
                response.addHeaderObject(
                    RequestCorrelationFilter.HEADER_NAME,
                    newCorrelationHeader()
                );
            }
        }
    }

    private static Header newCorrelationHeader() {
        StringSchema schema =
            new StringSchema();

        schema.setMaxLength(
            MAXIMUM_LENGTH
        );

        schema.setPattern(
            VALUE_PATTERN
        );

        Header header =
            new Header();

        header.setDescription(
            DESCRIPTION
        );

        header.setRequired(
            true
        );

        header.setSchema(
            schema
        );

        return header;
    }
}