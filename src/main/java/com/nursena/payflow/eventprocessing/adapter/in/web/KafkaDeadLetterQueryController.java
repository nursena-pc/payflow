package com.nursena.payflow.eventprocessing.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.port.in.GetKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in.ListKafkaDeadLetterRecordsQuery;
import com.nursena.payflow.eventprocessing.application.port.in.ListKafkaDeadLetterRecordsUseCase;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Kafka Dead Letters",
    description =
        "Authorized operations for querying "
            + "Kafka dead-letter records."
)
@RestController
@RequestMapping(
    "/api/v1/operations/kafka/dead-letters"
)
public class KafkaDeadLetterQueryController {

    private final ListKafkaDeadLetterRecordsUseCase
        listUseCase;

    private final GetKafkaDeadLetterRecordUseCase
        getUseCase;

    public KafkaDeadLetterQueryController(
        ListKafkaDeadLetterRecordsUseCase listUseCase,
        GetKafkaDeadLetterRecordUseCase getUseCase
    ) {
        this.listUseCase =
            Objects.requireNonNull(
                listUseCase,
                "listUseCase must not be null"
            );

        this.getUseCase =
            Objects.requireNonNull(
                getUseCase,
                "getUseCase must not be null"
            );
    }

    @Operation(
        operationId = "listKafkaDeadLetterRecords",
        summary = "List Kafka dead-letter records",
        description =
            "Returns safe operational metadata ordered "
                + "by receive time descending and "
                + "record identifier descending."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description =
                "Dead-letter records returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        KafkaDeadLetterRecordPageResponse
                            .class
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "A pagination or status value is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "403",
            description =
                "The authenticated principal does not "
                    + "have operations authority."
        )
    })
    @GetMapping
    public ResponseEntity<KafkaDeadLetterRecordPageResponse>
    listKafkaDeadLetterRecords(
        @Parameter(
            description = "Zero-based page index.",
            example = "0"
        )
        @RequestParam(
            name = "page",
            defaultValue = "0"
        )
        @Min(
            value = 0,
            message = "page must not be negative"
        )
        int page,

        @Parameter(
            description =
                "Number of records returned per page.",
            example = "20"
        )
        @RequestParam(
            name = "size",
            defaultValue = "20"
        )
        @Min(
            value = 1,
            message = "size must be greater than zero"
        )
        @Max(
            value =
                ListKafkaDeadLetterRecordsQuery.MAX_SIZE,
            message = "size must not exceed 100"
        )
        int size,

        @Parameter(
            description =
                "Optional dead-letter administration "
                    + "status filter.",
            example = "REPLAY_FAILED"
        )
        @RequestParam(
            name = "status",
            required = false
        )
        KafkaDeadLetterRecordStatus status
    ) {
        KafkaDeadLetterRecordPage result =
            listUseCase.listKafkaDeadLetterRecords(
                new ListKafkaDeadLetterRecordsQuery(
                    page,
                    size,
                    new KafkaDeadLetterRecordFilter(
                        status
                    )
                )
            );

        return ResponseEntity.ok(
            KafkaDeadLetterRecordPageResponse.from(
                result
            )
        );
    }

    @Operation(
        operationId = "getKafkaDeadLetterRecord",
        summary = "Get a Kafka dead-letter record",
        description =
            "Returns safe operational details for "
                + "one dead-letter record."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description =
                "Dead-letter record returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        KafkaDeadLetterRecordDetailsResponse
                            .class
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "The record identifier is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description =
                "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "403",
            description =
                "The authenticated principal does not "
                    + "have operations authority."
        ),
        @ApiResponse(
            responseCode = "404",
            description =
                "The dead-letter record was not found.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        )
    })
    @GetMapping("/{recordId}")
    public ResponseEntity<KafkaDeadLetterRecordDetailsResponse>
    getKafkaDeadLetterRecord(
        @Parameter(
            description =
                "Dead-letter record identifier.",
            schema = @Schema(
                type = "string",
                format = "uuid"
            )
        )
        @PathVariable("recordId")
        UUID recordId
    ) {
        KafkaDeadLetterRecordDetails result =
            getUseCase.getKafkaDeadLetterRecord(
                recordId
            );

        return ResponseEntity.ok(
            KafkaDeadLetterRecordDetailsResponse.from(
                result
            )
        );
    }
}
