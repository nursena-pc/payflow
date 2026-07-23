package com.nursena.payflow.eventprocessing.adapter.in.web;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "KafkaDeadLetterReplayResponse",
    description =
        "Result of a successfully completed "
            + "Kafka dead-letter replay."
)
public record KafkaDeadLetterReplayResponse(

    @Schema(
        description =
            "Identifier of the replayed "
                + "dead-letter record.",
        format = "uuid"
    )
    UUID recordId,

    @Schema(
        description =
            "Persisted status after replay.",
        example = "REPLAYED",
        allowableValues = {
            "REPLAYED"
        }
    )
    KafkaDeadLetterRecordStatus status
) {

    public KafkaDeadLetterReplayResponse {
        recordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        status =
            Objects.requireNonNull(
                status,
                "status must not be null"
            );

        if (
            status
                != KafkaDeadLetterRecordStatus
                .REPLAYED
        ) {
            throw new IllegalArgumentException(
                "Replay response status must be "
                    + "REPLAYED."
            );
        }
    }

    static KafkaDeadLetterReplayResponse replayed(
        UUID recordId
    ) {
        return new KafkaDeadLetterReplayResponse(
            recordId,
            KafkaDeadLetterRecordStatus.REPLAYED
        );
    }
}
