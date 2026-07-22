package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterRecordSummaryResponse",
    description =
        "Safe operational metadata for a Kafka "
            + "dead-letter record."
)
public record KafkaDeadLetterRecordSummaryResponse(

    @Schema(
        description = "Dead-letter record identifier.",
        format = "uuid"
    )
    UUID id,

    @Schema(
        description = "Current administration status.",
        example = "REPLAY_FAILED"
    )
    KafkaDeadLetterRecordStatus status,

    @Schema(
        description = "Kafka dead-letter topic.",
        example = "wallet.transfer.completed.dlt"
    )
    String deadLetterTopic,

    @Schema(
        description = "Dead-letter partition.",
        example = "0",
        minimum = "0"
    )
    int deadLetterPartition,

    @Schema(
        description = "Dead-letter offset.",
        example = "203",
        minimum = "0"
    )
    long deadLetterOffset,

    @Schema(
        description = "Original Kafka topic.",
        example = "wallet.transfer.completed"
    )
    String originalTopic,

    @Schema(
        description = "Original Kafka partition.",
        example = "0",
        minimum = "0"
    )
    int originalPartition,

    @Schema(
        description = "Original Kafka offset.",
        example = "103",
        minimum = "0"
    )
    long originalOffset,

    @Schema(
        description = "Original Kafka consumer group.",
        example = "payflow-transfer-completed-audit-v1"
    )
    String originalConsumerGroup,

    @Schema(
        description = "Exception type recorded by the consumer.",
        example = "java.lang.IllegalStateException"
    )
    String exceptionType,

    @Schema(
        description =
            "Replay attempts performed for this record.",
        example = "1",
        minimum = "0"
    )
    int replayCount,

    @Schema(
        description =
            "Replay attempts inherited from prior "
                + "records in the replay lineage.",
        example = "2",
        minimum = "0"
    )
    int replayAttemptBase,

    @Schema(
        description =
            "Total replay attempts across the lineage.",
        example = "3",
        minimum = "0"
    )
    int totalReplayAttempts,

    @Schema(
        description = "Time the dead-letter record was received.",
        format = "date-time"
    )
    Instant receivedAt,

    @Schema(
        description = "Most recent replay attempt time.",
        format = "date-time"
    )
    Instant lastReplayedAt,

    @Schema(
        description = "Original record in the replay lineage.",
        format = "uuid"
    )
    UUID replayOriginId,

    @Schema(
        description =
            "Whether replayable payload content exists.",
        example = "true"
    )
    boolean payloadAvailable
) {

    static KafkaDeadLetterRecordSummaryResponse from(
        KafkaDeadLetterRecordSummary summary
    ) {
        KafkaDeadLetterRecordSummary validatedSummary =
            Objects.requireNonNull(
                summary,
                "summary must not be null"
            );

        return new KafkaDeadLetterRecordSummaryResponse(
            validatedSummary.id(),
            validatedSummary.status(),
            validatedSummary.deadLetterTopic(),
            validatedSummary.deadLetterPartition(),
            validatedSummary.deadLetterOffset(),
            validatedSummary.originalTopic(),
            validatedSummary.originalPartition(),
            validatedSummary.originalOffset(),
            validatedSummary.originalConsumerGroup(),
            validatedSummary.exceptionType(),
            validatedSummary.replayCount(),
            validatedSummary.replayAttemptBase(),
            validatedSummary.totalReplayAttempts(),
            validatedSummary.receivedAt(),
            validatedSummary.lastReplayedAt(),
            validatedSummary.replayOriginId(),
            validatedSummary.payloadAvailable()
        );
    }
}
