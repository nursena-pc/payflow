package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterRecordDetailsResponse",
    description =
        "Safe operational details of a Kafka "
            + "dead-letter record."
)
public record KafkaDeadLetterRecordDetailsResponse(
    UUID id,
    KafkaDeadLetterRecordStatus status,
    String deadLetterTopic,
    int deadLetterPartition,
    long deadLetterOffset,
    String originalTopic,
    int originalPartition,
    long originalOffset,
    String originalConsumerGroup,
    String exceptionType,
    int replayCount,
    int replayAttemptBase,
    int totalReplayAttempts,
    Instant receivedAt,
    Instant lastReplayedAt,
    UUID replayOriginId,
    boolean payloadAvailable,

    @Schema(
        description =
            "Exception message recorded during "
                + "dead-letter intake."
    )
    String exceptionMessage,

    @Schema(
        description =
            "Most recent controlled replay error."
    )
    String lastReplayError,

    @Schema(
        description =
            "Expiration time of an active replay lease.",
        format = "date-time"
    )
    Instant replayLeaseUntil
) {

    static KafkaDeadLetterRecordDetailsResponse from(
        KafkaDeadLetterRecordDetails details
    ) {
        KafkaDeadLetterRecordDetails validatedDetails =
            Objects.requireNonNull(
                details,
                "details must not be null"
            );

        KafkaDeadLetterRecordSummary summary =
            validatedDetails.summary();

        return new KafkaDeadLetterRecordDetailsResponse(
            summary.id(),
            summary.status(),
            summary.deadLetterTopic(),
            summary.deadLetterPartition(),
            summary.deadLetterOffset(),
            summary.originalTopic(),
            summary.originalPartition(),
            summary.originalOffset(),
            summary.originalConsumerGroup(),
            summary.exceptionType(),
            summary.replayCount(),
            summary.replayAttemptBase(),
            summary.totalReplayAttempts(),
            summary.receivedAt(),
            summary.lastReplayedAt(),
            summary.replayOriginId(),
            summary.payloadAvailable(),
            validatedDetails.exceptionMessage(),
            validatedDetails.lastReplayError(),
            validatedDetails.replayLeaseUntil()
        );
    }
}
