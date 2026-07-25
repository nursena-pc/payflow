package com.nursena.payflow.eventprocessing.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration.OpenApiConfiguration;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.in
    .GetKafkaDeadLetterCommandAuditTimelineUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsQuery;
import com.nursena.payflow.eventprocessing.application.port.in
    .ListKafkaDeadLetterCommandAuditsUseCase;
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
    name = "Kafka Dead-letter Command Audits",
    description =
        "Authorized operations for querying the "
            + "append-only command audit trail."
)
@RestController
@RequestMapping(
    "/api/v1/operations/kafka/"
        + "dead-letter-command-audits"
)
public class KafkaDeadLetterCommandAuditController {

    private final ListKafkaDeadLetterCommandAuditsUseCase
        listUseCase;

    private final GetKafkaDeadLetterCommandAuditTimelineUseCase
        getTimelineUseCase;

    public KafkaDeadLetterCommandAuditController(
        ListKafkaDeadLetterCommandAuditsUseCase listUseCase,
        GetKafkaDeadLetterCommandAuditTimelineUseCase
            getTimelineUseCase
    ) {
        this.listUseCase =
            Objects.requireNonNull(
                listUseCase,
                "listUseCase must not be null"
            );
        this.getTimelineUseCase =
            Objects.requireNonNull(
                getTimelineUseCase,
                "getTimelineUseCase must not be null"
            );
    }

    @Operation(
        operationId = "listKafkaDeadLetterCommandAudits",
        summary = "List Kafka dead-letter command audits",
        description =
            "Returns safe audit metadata ordered by "
                + "occurrence time descending and audit "
                + "identifier descending."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Command audit entries returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        KafkaDeadLetterCommandAuditPageResponse
                            .class
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "A pagination or filter value is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "403",
            description =
                "The authenticated principal does not "
                    + "have operations authority."
        )
    })
    @GetMapping
    public ResponseEntity<KafkaDeadLetterCommandAuditPageResponse>
    listKafkaDeadLetterCommandAudits(
        @RequestParam(
            name = "page",
            defaultValue = "0"
        )
        @Min(
            value = 0,
            message = "page must not be negative"
        )
        int page,
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
                ListKafkaDeadLetterCommandAuditsQuery.MAX_SIZE,
            message = "size must not exceed 100"
        )
        int size,
        @RequestParam(
            name = "commandId",
            required = false
        )
        UUID commandId,
        @RequestParam(
            name = "operatorId",
            required = false
        )
        UUID operatorId,
        @RequestParam(
            name = "deadLetterRecordId",
            required = false
        )
        UUID deadLetterRecordId,
        @RequestParam(
            name = "commandType",
            required = false
        )
        KafkaDeadLetterCommandType commandType,
        @RequestParam(
            name = "stage",
            required = false
        )
        KafkaDeadLetterCommandAuditStage stage,
        @RequestParam(
            name = "outcome",
            required = false
        )
        KafkaDeadLetterCommandAuditOutcome outcome
    ) {
        KafkaDeadLetterCommandAuditPage result =
            listUseCase.listKafkaDeadLetterCommandAudits(
                new ListKafkaDeadLetterCommandAuditsQuery(
                    page,
                    size,
                    new KafkaDeadLetterCommandAuditFilter(
                        commandId,
                        operatorId,
                        deadLetterRecordId,
                        commandType,
                        stage,
                        outcome
                    )
                )
            );

        return ResponseEntity.ok(
            KafkaDeadLetterCommandAuditPageResponse.from(
                result
            )
        );
    }

    @Operation(
        operationId = "getKafkaDeadLetterCommandAuditTimeline",
        summary = "Get a command audit timeline",
        description =
            "Returns ATTEMPTED and, when available, "
                + "COMPLETED audit entries in chronological order."
    )
    @SecurityRequirement(
        name = OpenApiConfiguration.BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Command audit timeline returned.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        KafkaDeadLetterCommandAuditTimelineResponse
                            .class
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "The command identifier is invalid.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Bearer token is missing or invalid."
        ),
        @ApiResponse(
            responseCode = "403",
            description =
                "The authenticated principal does not "
                    + "have operations authority."
        ),
        @ApiResponse(
            responseCode = "404",
            description = "The command audit timeline was not found.",
            content = @Content(
                mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation = ApiError.class
                )
            )
        )
    })
    @GetMapping("/{commandId}")
    public ResponseEntity<
        KafkaDeadLetterCommandAuditTimelineResponse
    > getKafkaDeadLetterCommandAuditTimeline(
        @Parameter(
            description = "Operator command identifier.",
            schema = @Schema(
                type = "string",
                format = "uuid"
            )
        )
        @PathVariable("commandId")
        UUID commandId
    ) {
        KafkaDeadLetterCommandAuditTimeline result =
            getTimelineUseCase
                .getKafkaDeadLetterCommandAuditTimeline(
                    commandId
                );

        return ResponseEntity.ok(
            KafkaDeadLetterCommandAuditTimelineResponse.from(
                result
            )
        );
    }
}
