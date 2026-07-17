package com.nursena.payflow.common.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "SystemHealthResponse",
    description = "Basic PayFlow service health information."
)
public record SystemHealthResponse(

    @Schema(
        description = "Current service status.",
        example = "UP"
    )
    String status,

    @Schema(
        description = "Service identifier.",
        example = "payflow"
    )
    String service,

    @Schema(
        description = "Time at which the status was generated.",
        example = "2026-07-17T12:00:00Z",
        format = "date-time"
    )
    Instant timestamp
) {
}
