package com.nursena.payflow.eventprocessing.adapter.in.web;

import static org.springframework.http.MediaType
    .APPLICATION_JSON_VALUE;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.common.api.ApiError;
import com.nursena.payflow.configuration
    .OpenApiConfiguration;
import com.nursena.payflow.eventprocessing.application.model
    .DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model
    .DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model
    .ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model
    .ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in
    .DiscardKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.in
    .ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterCommandException;
import com.nursena.payflow.eventprocessing.domain.exception
    .KafkaDeadLetterRecordNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses
    .ApiResponse;
import io.swagger.v3.oas.annotations.responses
    .ApiResponses;
import io.swagger.v3.oas.annotations.security
    .SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation
    .RequestMapping;
import org.springframework.web.bind.annotation
    .RestController;

@Tag(
    name = "Kafka Dead-Letter Commands",
    description =
        "Authorized operations for replaying "
            + "or discarding Kafka dead-letter "
            + "records."
)
@RestController
@RequestMapping(
    "/api/v1/operations/kafka/dead-letters"
)
public class KafkaDeadLetterCommandController {

    private final ReplayKafkaDeadLetterRecordUseCase
        replayUseCase;

    private final DiscardKafkaDeadLetterRecordUseCase
        discardUseCase;

    public KafkaDeadLetterCommandController(
        ReplayKafkaDeadLetterRecordUseCase
            replayUseCase,
        DiscardKafkaDeadLetterRecordUseCase
            discardUseCase
    ) {
        this.replayUseCase =
            Objects.requireNonNull(
                replayUseCase,
                "replayUseCase must not be null"
            );

        this.discardUseCase =
            Objects.requireNonNull(
                discardUseCase,
                "discardUseCase must not be null"
            );
    }

    @Operation(
        operationId =
            "replayKafkaDeadLetterRecord",
        summary =
            "Replay a Kafka dead-letter record",
        description =
            "Claims the record through the "
                + "controlled replay lifecycle, "
                + "publishes it to the original "
                + "topic, and persists the outcome."
    )
    @SecurityRequirement(
        name =
            OpenApiConfiguration
                .BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description =
                "The record was replayed.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        KafkaDeadLetterReplayResponse
                            .class
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "The record identifier is invalid.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
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
                "The authenticated principal does "
                    + "not have operations authority."
        ),
        @ApiResponse(
            responseCode = "404",
            description =
                "The dead-letter record was not found.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The dead-letter record cannot be "
                    + "replayed in its current state.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "502",
            description =
                "Kafka replay publication failed.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "503",
            description =
                "The replay outcome could not be "
                    + "resolved safely.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        )
    })
    @PostMapping("/{recordId}/replay")
    public ResponseEntity<KafkaDeadLetterReplayResponse>
    replayKafkaDeadLetterRecord(
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
        ReplayKafkaDeadLetterRecordResult result =
            Objects.requireNonNull(
                replayUseCase.replay(
                    new ReplayKafkaDeadLetterRecordCommand(
                        recordId
                    )
                ),
                "replay result must not be null"
            );

        return replayResponse(
            recordId,
            result
        );
    }

    @Operation(
        operationId =
            "discardKafkaDeadLetterRecord",
        summary =
            "Discard a Kafka dead-letter record",
        description =
            "Atomically transitions a RECEIVED or "
                + "REPLAY_FAILED record to DISCARDED. "
                + "Repeated discard requests are "
                + "idempotent."
    )
    @SecurityRequirement(
        name =
            OpenApiConfiguration
                .BEARER_AUTH_SCHEME
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description =
                "The record is discarded."
        ),
        @ApiResponse(
            responseCode = "400",
            description =
                "The record identifier is invalid.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
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
                "The authenticated principal does "
                    + "not have operations authority."
        ),
        @ApiResponse(
            responseCode = "404",
            description =
                "The dead-letter record was not found.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description =
                "The dead-letter record cannot be "
                    + "discarded in its current state.",
            content = @Content(
                mediaType =
                    APPLICATION_JSON_VALUE,
                schema = @Schema(
                    implementation =
                        ApiError.class
                )
            )
        )
    })
    @PostMapping("/{recordId}/discard")
    public ResponseEntity<Void>
    discardKafkaDeadLetterRecord(
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
        DiscardKafkaDeadLetterRecordResult result =
            Objects.requireNonNull(
                discardUseCase.discard(
                    new DiscardKafkaDeadLetterRecordCommand(
                        recordId
                    )
                ),
                "discard result must not be null"
            );

        return discardResponse(
            recordId,
            result
        );
    }

    private static ResponseEntity<
        KafkaDeadLetterReplayResponse
        > replayResponse(
        UUID recordId,
        ReplayKafkaDeadLetterRecordResult result
    ) {
        return switch (result.outcome()) {
            case REPLAYED ->
                ResponseEntity.ok(
                    KafkaDeadLetterReplayResponse
                        .replayed(recordId)
                );

            case NOT_FOUND ->
                throw new
                    KafkaDeadLetterRecordNotFoundException(
                    recordId
                );

            case NOT_CLAIMABLE ->
                throw KafkaDeadLetterCommandException
                    .notClaimable(recordId);

            case REPLAY_FAILED ->
                throw KafkaDeadLetterCommandException
                    .replayFailed(recordId);

            case UNRESOLVED ->
                throw KafkaDeadLetterCommandException
                    .replayUnresolved(recordId);
        };
    }

    private static ResponseEntity<Void>
    discardResponse(
        UUID recordId,
        DiscardKafkaDeadLetterRecordResult result
    ) {
        return switch (result.outcome()) {
            case DISCARDED,
                 ALREADY_DISCARDED ->
                ResponseEntity
                    .noContent()
                    .build();

            case NOT_FOUND ->
                throw new
                    KafkaDeadLetterRecordNotFoundException(
                    recordId
                );

            case NOT_DISCARDABLE ->
                throw KafkaDeadLetterCommandException
                    .notDiscardable(recordId);
        };
    }
}
