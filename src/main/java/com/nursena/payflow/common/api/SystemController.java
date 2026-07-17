package com.nursena.payflow.common.api;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.Instant;

import com.nursena.payflow.configuration.OpenApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "System",
    description = "Public service status operations."
)
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Operation(
        operationId = "getSystemHealth",
        summary = "Get service health",
        description =
            "Returns basic PayFlow service status information."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Service health returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        SystemHealthResponse.class
                ),
                examples = @ExampleObject(
                    value =
                        OpenApiExamples.SYSTEM_HEALTH
                )
            )
        )
    })
    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> health() {
        SystemHealthResponse response =
            new SystemHealthResponse(
                "UP",
                "payflow",
                Instant.now()
            );

        return ResponseEntity.ok(response);
    }
}
