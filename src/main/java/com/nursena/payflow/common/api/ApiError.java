package com.nursena.payflow.common.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "ApiError",
    description = "Stable error response returned by PayFlow."
)
public record ApiError(

    @Schema(
        description = "Time at which the error was generated.",
        example = "2026-07-17T12:00:00Z",
        format = "date-time"
    )
    Instant timestamp,

    @Schema(
        description = "HTTP status code.",
        example = "400",
        format = "int32"
    )
    int status,

    @Schema(
        description = "Stable machine-readable error code.",
        example = "VALIDATION_FAILED"
    )
    String code,

    @Schema(
        description = "Human-readable error description.",
        example = "Request validation failed."
    )
    String message,

    @Schema(
        description = "Request path that produced the error.",
        example = "/api/v1/auth/register"
    )
    String path,

    @Schema(
        description =
            "Field-level violations. Empty for "
                + "non-validation errors."
    )
    List<FieldViolation> violations
) {

    @Schema(
        name = "FieldViolation",
        description = "A request-field validation failure."
    )
    public record FieldViolation(

        @Schema(
            description = "Invalid request field.",
            example = "email"
        )
        String field,

        @Schema(
            description = "Field validation message.",
            example = "Email must be valid."
        )
        String message
    ) {
    }
}
